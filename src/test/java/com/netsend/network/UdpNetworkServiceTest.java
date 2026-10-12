package com.netsend.network;

import com.netsend.config.AppConfig;
import com.netsend.model.MessageType;
import com.netsend.model.ProtocolPacket;
import com.netsend.model.TargetType;
import com.netsend.protocol.PacketCodec;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UdpNetworkServiceTest {
    @Test void receivesRealUdpDatagramAndReportsSourceAddress() throws Exception {
        int port;
        try (DatagramSocket probe = new DatagramSocket(0)) { port = probe.getLocalPort(); }
        AppConfig config = new AppConfig();
        config.setListenPort(port);
        config.setGroups(new ArrayList<>());
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<ReceivedDatagram> result = new AtomicReference<>();
        try (UdpNetworkService service = new UdpNetworkService()) {
            service.start(config, datagram -> { result.set(datagram); received.countDown(); }, message -> {});
            byte[] bytes = new PacketCodec().encode(ProtocolPacket.create(
                    MessageType.CHAT_MESSAGE, "loopback-peer", TargetType.DIRECT, "127.0.0.1", "test payload"));
            try (DatagramSocket sender = new DatagramSocket()) {
                sender.send(new DatagramPacket(bytes, bytes.length, new InetSocketAddress(InetAddress.getLoopbackAddress(), port)));
            }
            assertTrue(received.await(3, TimeUnit.SECONDS), "receiver should get a real local UDP packet");
            assertEquals("test payload", result.get().packet().payload());
            assertEquals(InetAddress.getLoopbackAddress().getHostAddress(), result.get().sourceAddress().getHostAddress());
        }
    }
}
