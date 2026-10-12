package com.netsend.network;

import com.netsend.config.AppConfig;
import com.netsend.model.GroupConfig;
import com.netsend.model.ProtocolPacket;
import com.netsend.protocol.PacketCodec;
import com.netsend.util.NetworkValidation;
import com.netsend.util.SlidingWindowRateLimiter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UdpNetworkService implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(UdpNetworkService.class);
    private final PacketCodec codec = new PacketCodec();
    private final ExecutorService receivers = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "netsend-udp-receiver"); t.setDaemon(true); return t;
    });
    private final AtomicBoolean running = new AtomicBoolean();
    private final SlidingWindowRateLimiter inboundLimiter =
            new SlidingWindowRateLimiter(200, java.time.Duration.ofSeconds(1));
    private final AtomicLong lastRateWarningMillis = new AtomicLong();
    private final List<MulticastSocket> groupSockets = new CopyOnWriteArrayList<>();
    private volatile DatagramSocket receiveSocket;
    private volatile AppConfig config;
    private volatile Consumer<ReceivedDatagram> receiver = packet -> {};
    private volatile Consumer<String> diagnostics = message -> {};
    private volatile NetworkInterface networkInterface;

    public synchronized void start(AppConfig config, Consumer<ReceivedDatagram> receiver, Consumer<String> diagnostics) throws IOException {
        stop();
        this.config = config;
        this.receiver = receiver;
        this.diagnostics = diagnostics;
        if (config.getListenPort() < 1 || config.getListenPort() > 65535) throw new IllegalArgumentException("Port lắng nghe không hợp lệ.");
        networkInterface = resolveInterface(config.getInterfaceAddress());
        DatagramSocket socket = new DatagramSocket(null);
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(config.getListenPort()));
        receiveSocket = socket;
        running.set(true);
        receivers.execute(() -> receiveLoop(socket, "udp"));
        joinConfiguredGroups(config.getGroups());
        diagnostics.accept("Đang lắng nghe UDP tại cổng " + config.getListenPort() + ". Socket sẵn sàng; chưa xác nhận máy khác có thể truy cập.");
    }

    public synchronized void joinConfiguredGroups(List<GroupConfig> groups) {
        leaveAllGroups();
        if (!running.get()) return;
        for (GroupConfig group : groups) {
            if (!group.isJoined()) continue;
            try {
                NetworkValidation.multicastAddress(group.getAddress());
                NetworkValidation.port(group.getPort());
                MulticastSocket socket = new MulticastSocket(null);
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(group.getPort()));
                InetAddress address = NetworkValidation.ipv4(group.getAddress());
                socket.joinGroup(new InetSocketAddress(address, group.getPort()), networkInterface);
                groupSockets.add(socket);
                receivers.execute(() -> receiveLoop(socket, "multicast " + group.getName()));
                diagnostics.accept("Đã tham gia " + group.getName() + " (" + group.getAddress() + ":" + group.getPort() + ").");
            } catch (IOException | IllegalArgumentException e) {
                diagnostics.accept("Không thể tham gia group " + group.getName() + ": " + e.getMessage());
                log.warn("Cannot join multicast group {}", group.getName(), e);
            }
        }
    }

    public void send(ProtocolPacket packet, String address, int port, boolean broadcast) throws IOException {
        byte[] bytes = codec.encode(packet);
        InetAddress destination = NetworkValidation.ipv4(address);
        NetworkValidation.port(port);
        try (DatagramSocket socket = new DatagramSocket()) {
            if (config.getInterfaceAddress() != null && !config.getInterfaceAddress().isBlank())
                socket.bind(new InetSocketAddress(NetworkValidation.ipv4(config.getInterfaceAddress()), 0));
            if (broadcast) socket.setBroadcast(true);
            socket.send(new DatagramPacket(bytes, bytes.length, destination, port));
        }
    }

    private void receiveLoop(DatagramSocket socket, String source) {
        byte[] buffer = new byte[PacketCodec.MAX_DATAGRAM_BYTES + 1];
        while (running.get() && !socket.isClosed()) {
            DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(datagram);
                if (!inboundLimiter.tryAcquire()) {
                    long now = System.currentTimeMillis();
                    long previous = lastRateWarningMillis.get();
                    if (now - previous >= 5000 && lastRateWarningMillis.compareAndSet(previous, now))
                        diagnostics.accept("Đang bỏ datagram do vượt giới hạn xử lý 200 gói/giây.");
                    continue;
                }
                if (datagram.getLength() > PacketCodec.MAX_DATAGRAM_BYTES) {
                    diagnostics.accept("Bỏ gói UDP vượt kích thước tối đa từ " + datagram.getAddress().getHostAddress());
                    continue;
                }
                try {
                    ReceivedDatagram received = new ReceivedDatagram(codec.decode(datagram.getData(), datagram.getLength()), datagram.getAddress());
                    try {
                        receiver.accept(received);
                    } catch (RuntimeException e) {
                        diagnostics.accept("Lỗi xử lý datagram: " + e.getMessage());
                        log.error("Datagram callback failed", e);
                    }
                } catch (IOException | IllegalArgumentException e) {
                    diagnostics.accept("Bỏ gói tin không hợp lệ (" + source + "): " + e.getMessage());
                }
            } catch (SocketException e) {
                if (running.get()) diagnostics.accept("Lỗi socket nhận (" + source + "): " + e.getMessage());
                break;
            } catch (IOException e) {
                if (running.get()) diagnostics.accept("Lỗi nhận UDP (" + source + "): " + e.getMessage());
            }
        }
    }

    private NetworkInterface resolveInterface(String address) throws IOException {
        if (address == null || address.isBlank()) return null;
        InetAddress inet = NetworkValidation.ipv4(address);
        NetworkInterface result = NetworkInterface.getByInetAddress(inet);
        if (result == null || !result.isUp()) throw new IOException("Không tìm thấy card mạng đang hoạt động cho " + address);
        return result;
    }

    public List<String> localIpv4Addresses() {
        List<String> addresses = new ArrayList<>();
        try {
            var interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                var inetAddresses = ni.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress address = inetAddresses.nextElement();
                    if (address.getAddress().length == 4) addresses.add(address.getHostAddress());
                }
            }
        } catch (SocketException e) {
            log.warn("Could not enumerate network interfaces", e);
        }
        return addresses;
    }

    public synchronized void leaveAllGroups() {
        for (MulticastSocket socket : groupSockets) socket.close();
        groupSockets.clear();
    }

    public boolean isRunning() { return running.get(); }

    public synchronized void stop() {
        running.set(false);
        if (receiveSocket != null) receiveSocket.close();
        receiveSocket = null;
        leaveAllGroups();
    }

    @Override public void close() {
        stop();
        receivers.shutdownNow();
    }
}
