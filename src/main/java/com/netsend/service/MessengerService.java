package com.netsend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.netsend.config.AppConfig;
import com.netsend.model.ChatMessage;
import com.netsend.model.Device;
import com.netsend.model.GroupConfig;
import com.netsend.model.MessageType;
import com.netsend.model.ProtocolPacket;
import com.netsend.model.TargetType;
import com.netsend.network.ReceivedDatagram;
import com.netsend.network.UdpNetworkService;
import com.netsend.repository.JsonRepository;
import com.netsend.util.NetworkValidation;
import com.netsend.util.SlidingWindowRateLimiter;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessengerService implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(MessengerService.class);
    private final UdpNetworkService network = new UdpNetworkService();
    private final DuplicateMessageGuard duplicateGuard = new DuplicateMessageGuard(Duration.ofMinutes(5));
    private final SlidingWindowRateLimiter outboundLimiter = new SlidingWindowRateLimiter(20, Duration.ofSeconds(1));
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Path dataDirectory;
    private final Path configPath;
    private final Path historyPath;
    private final JsonRepository<ChatMessage> historyRepository;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "netsend-heartbeat"); t.setDaemon(true); return t;
    });
    private final Map<String, Device> devices = new ConcurrentHashMap<>();
    private final List<ChatMessage> history = new CopyOnWriteArrayList<>();
    private volatile AppConfig config;
    private volatile Consumer<ChatMessage> messageListener = message -> {};
    private volatile Consumer<String> logListener = message -> {};
    private volatile Consumer<Device> deviceListener = device -> {};
    private volatile ScheduledFuture<?> heartbeatTask;
    private final String instanceId = UUID.randomUUID().toString();

    public MessengerService() {
        this(Path.of(System.getProperty("netsend.dataDir",
                Path.of(System.getProperty("user.home"), ".netsend-lan").toString())));
    }

    public MessengerService(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        configPath = dataDirectory.resolve("config.json");
        historyPath = dataDirectory.resolve("history.json");
        historyRepository = new JsonRepository<>(historyPath, new TypeReference<List<ChatMessage>>() {}, List::of);
        config = loadConfig();
        applyRuntimeOverrides();
        loadHistory();
    }

    public AppConfig getConfig() { return config; }
    public List<ChatMessage> getHistory() { return List.copyOf(history); }
    public List<Device> getDevices() { return devices.values().stream().sorted((a, b) -> a.name().compareToIgnoreCase(b.name())).toList(); }
    public void onMessage(Consumer<ChatMessage> listener) { messageListener = listener; }
    public void onLog(Consumer<String> listener) { logListener = listener; }
    public void onDevice(Consumer<Device> listener) { deviceListener = listener; }
    public List<String> localIpv4Addresses() { return network.localIpv4Addresses(); }
    public boolean isRunning() { return network.isRunning(); }

    public synchronized void saveConfig(AppConfig newConfig) throws IOException {
        validateConfig(newConfig);
        this.config = newConfig;
        FilesSupport.writeJson(configPath, mapper, newConfig);
    }

    public synchronized void start() throws IOException {
        network.start(config, this::handleDatagram, this::log);
        if (heartbeatTask == null || heartbeatTask.isCancelled() || heartbeatTask.isDone())
            heartbeatTask = scheduler.scheduleAtFixedRate(this::sendHeartbeat, 1, 10, TimeUnit.SECONDS);
        sendDiscovery();
    }

    public synchronized void restart() throws IOException {
        stop();
        start();
    }

    public synchronized void stop() {
        if (heartbeatTask != null) heartbeatTask.cancel(false);
        network.stop();
    }

    public void sendDirect(String ip, String content) throws IOException {
        sendDirect(ip, config.getListenPort(), content);
    }

    public void sendDirect(String ip, int port, String content) throws IOException {
        checkSendRate();
        NetworkValidation.message(content);
        NetworkValidation.ipv4(ip);
        NetworkValidation.port(port);
        ProtocolPacket packet = createPacket(MessageType.CHAT_MESSAGE, TargetType.DIRECT, ip, content);
        network.send(packet, ip, port, false);
        recordOutgoing(packet, ip + ":" + port, "UDP sent; delivery unconfirmed");
    }

    public void sendGroup(String groupName, String content) throws IOException {
        checkSendRate();
        NetworkValidation.message(content);
        GroupConfig group = config.getGroups().stream().filter(g -> g.getName().equalsIgnoreCase(groupName)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhóm " + groupName));
        NetworkValidation.multicastAddress(group.getAddress());
        ProtocolPacket packet = createPacket(MessageType.GROUP_MESSAGE, TargetType.GROUP, group.getName(), content);
        network.send(packet, group.getAddress(), group.getPort(), false);
        recordOutgoing(packet, group.getAddress() + ":" + group.getPort(), "UDP multicast sent; delivery unconfirmed");
    }

    public void sendBroadcast(String content) throws IOException {
        checkSendRate();
        NetworkValidation.message(content);
        NetworkValidation.ipv4(config.getBroadcastAddress());
        ProtocolPacket packet = createPacket(MessageType.BROADCAST_MESSAGE, TargetType.BROADCAST, "*", content);
        network.send(packet, config.getBroadcastAddress(), config.getListenPort(), true);
        recordOutgoing(packet, config.getBroadcastAddress(), "UDP broadcast sent; delivery unconfirmed");
    }

    public void clearHistory() throws IOException {
        history.clear();
        persistHistory();
    }

    public synchronized void setGroups(List<GroupConfig> groups) throws IOException {
        AppConfig updated = config;
        updated.setGroups(new ArrayList<>(groups));
        saveConfig(updated);
        if (network.isRunning()) network.joinConfiguredGroups(groups);
    }

    private void handleDatagram(ReceivedDatagram datagram) {
        ProtocolPacket packet = datagram.packet();
        String sourceIp = datagram.sourceAddress().getHostAddress();
        if (!duplicateGuard.firstSeen(packet.messageId())) return;
        if (packet.senderInstanceId().equals(instanceId)) return;

        switch (packet.messageType()) {
            case DISCOVERY_REQUEST -> {
                int targetPort = advertisedPort(packet);
                if (targetPort > 0) sendDiscoveryResponse(datagram.sourceAddress(), targetPort);
            }
            case DISCOVERY_RESPONSE, HEARTBEAT -> updateDevice(packet.senderName(), sourceIp, advertisedPort(packet));
            case CHAT_MESSAGE, GROUP_MESSAGE, BROADCAST_MESSAGE -> {
                if (packet.messageType() == MessageType.GROUP_MESSAGE && !isJoined(packet.targetId())) return;
                ChatMessage message = new ChatMessage(packet.messageId(), false, packet.senderName(), sourceIp,
                        Instant.now(), packet.targetType(), packet.targetId(), packet.payload(), "Received");
                addMessage(message);
            }
        }
    }

    private void sendHeartbeat() {
        try {
            ProtocolPacket packet = createPacket(MessageType.HEARTBEAT, TargetType.SYSTEM, "", String.valueOf(config.getListenPort()));
            network.send(packet, config.getBroadcastAddress(), config.getListenPort(), true);
        } catch (IOException | IllegalArgumentException e) {
            log("Heartbeat/discovery broadcast unavailable: " + e.getMessage());
        }
    }

    private void sendDiscovery() {
        try {
            ProtocolPacket packet = createPacket(MessageType.DISCOVERY_REQUEST, TargetType.SYSTEM, "", String.valueOf(config.getListenPort()));
            network.send(packet, config.getBroadcastAddress(), config.getListenPort(), true);
        } catch (IOException | IllegalArgumentException e) {
            log("LAN discovery unavailable: " + e.getMessage());
        }
    }

    private void sendDiscoveryResponse(InetAddress address, int targetPort) {
        try {
            ProtocolPacket response = createPacket(MessageType.DISCOVERY_RESPONSE, TargetType.SYSTEM, "", String.valueOf(config.getListenPort()));
            network.send(response, address.getHostAddress(), targetPort, false);
        } catch (IOException | IllegalArgumentException e) {
            log("Không gửi được discovery response đến " + address.getHostAddress() + ": " + e.getMessage());
        }
    }

    private int advertisedPort(ProtocolPacket packet) {
        try {
            int port = Integer.parseInt(packet.payload());
            NetworkValidation.port(port);
            return port;
        } catch (IllegalArgumentException e) {
            log("Bỏ discovery/heartbeat có listen port không hợp lệ từ " + packet.senderName());
            return -1;
        }
    }

    private ProtocolPacket createPacket(MessageType type, TargetType target, String targetId, String payload) {
        return ProtocolPacket.create(type, config.getDisplayName(), instanceId, target, targetId, payload);
    }

    private void updateDevice(String name, String ip, int port) {
        if (port < 1) return;
        Device device = new Device(name, ip, port, Instant.now());
        devices.put(ip + ":" + port, device);
        deviceListener.accept(device);
    }

    private boolean isJoined(String groupName) {
        return config.getGroups().stream().anyMatch(g -> g.isJoined() && g.getName().equalsIgnoreCase(groupName));
    }

    private void recordOutgoing(ProtocolPacket packet, String destination, String status) {
        addMessage(new ChatMessage(packet.messageId(), true, config.getDisplayName(), destination, packet.timestamp(),
                packet.targetType(), packet.targetId(), packet.payload(), status));
    }

    private void addMessage(ChatMessage message) {
        history.add(message);
        try { persistHistory(); }
        catch (IOException e) { log("Không lưu được lịch sử: " + e.getMessage()); }
        messageListener.accept(message);
    }

    private void loadHistory() {
        if (!java.nio.file.Files.exists(historyPath)) return;
        try {
            history.addAll(historyRepository.load());
        } catch (IOException e) {
            log("Không đọc được history.json: " + e.getMessage());
        }
    }

    private void persistHistory() throws IOException {
        historyRepository.save(List.copyOf(history));
    }

    private AppConfig loadConfig() {
        try {
            if (java.nio.file.Files.exists(configPath)) return mapper.readValue(configPath.toFile(), AppConfig.class);
        } catch (IOException e) {
            log("Không đọc được cấu hình; dùng cấu hình mặc định: " + e.getMessage());
        }
        return new AppConfig();
    }

    private void applyRuntimeOverrides() {
        String displayName = System.getProperty("netsend.displayName");
        String listenPort = System.getProperty("netsend.listenPort");
        String broadcastAddress = System.getProperty("netsend.broadcastAddress");
        String interfaceAddress = System.getProperty("netsend.interfaceAddress");
        if (displayName != null) config.setDisplayName(displayName);
        if (listenPort != null) config.setListenPort(Integer.parseInt(listenPort));
        if (broadcastAddress != null) config.setBroadcastAddress(broadcastAddress);
        if (interfaceAddress != null) config.setInterfaceAddress(interfaceAddress);
    }

    private void validateConfig(AppConfig value) {
        if (value.getDisplayName() == null || value.getDisplayName().isBlank() || value.getDisplayName().length() > 80)
            throw new IllegalArgumentException("Tên hiển thị phải có từ 1 đến 80 ký tự.");
        NetworkValidation.port(value.getListenPort());
        NetworkValidation.ipv4(value.getBroadcastAddress());
        if (value.getInterfaceAddress() != null && !value.getInterfaceAddress().isBlank()) NetworkValidation.ipv4(value.getInterfaceAddress());
        for (GroupConfig group : value.getGroups()) {
            NetworkValidation.multicastAddress(group.getAddress());
            NetworkValidation.port(group.getPort());
            if (group.getName() == null || group.getName().isBlank()) throw new IllegalArgumentException("Tên nhóm không được để trống.");
        }
    }

    private void checkSendRate() {
        if (!outboundLimiter.tryAcquire()) throw new IllegalStateException("Đang gửi quá nhanh; hãy chờ một giây rồi thử lại.");
    }

    private void log(String message) {
        log.info(message);
        logListener.accept(Instant.now() + "  " + message);
    }

    @Override public void close() {
        stop();
        scheduler.shutdownNow();
        network.close();
    }

    private static final class FilesSupport {
        private static void writeJson(Path path, ObjectMapper mapper, Object value) throws IOException {
            java.nio.file.Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), value);
            try {
                java.nio.file.Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                java.nio.file.Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
