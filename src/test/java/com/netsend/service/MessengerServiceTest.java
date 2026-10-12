package com.netsend.service;

import com.netsend.config.AppConfig;
import com.netsend.model.ChatMessage;
import java.net.DatagramSocket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MessengerServiceTest {
    @TempDir Path directory;

    @Test void sendsAndReceivesUnicastBetweenTwoRealServiceInstances() throws Exception {
        int receiverPort = unusedPort();
        int senderPort = unusedPort();
        while (senderPort == receiverPort) senderPort = unusedPort();

        try (MessengerService receiver = new MessengerService(directory.resolve("receiver"));
             MessengerService sender = new MessengerService(directory.resolve("sender"))) {
            configure(receiver.getConfig(), "Receiver", receiverPort);
            configure(sender.getConfig(), "Sender", senderPort);
            CountDownLatch arrived = new CountDownLatch(1);
            AtomicReference<ChatMessage> incoming = new AtomicReference<>();
            receiver.onMessage(message -> { incoming.set(message); arrived.countDown(); });
            receiver.start();
            sender.start();

            sender.sendDirect("127.0.0.1", receiverPort, "unicast integration");

            assertTrue(arrived.await(3, TimeUnit.SECONDS));
            assertEquals("unicast integration", incoming.get().content());
            assertEquals("127.0.0.1", incoming.get().sourceIp());
            assertEquals("Sender", incoming.get().senderName());
            assertEquals(1, receiver.getHistory().size());
        }
    }

    private static int unusedPort() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(0)) { return socket.getLocalPort(); }
    }

    private static void configure(AppConfig config, String name, int port) {
        config.setDisplayName(name);
        config.setListenPort(port);
        config.setBroadcastAddress("127.0.0.1");
        config.setInterfaceAddress("");
        config.setGroups(new ArrayList<>());
    }
}
