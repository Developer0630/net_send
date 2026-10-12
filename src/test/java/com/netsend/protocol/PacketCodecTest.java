package com.netsend.protocol;

import com.netsend.model.MessageType;
import com.netsend.model.ProtocolPacket;
import com.netsend.model.TargetType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PacketCodecTest {
    private final PacketCodec codec = new PacketCodec();

    @Test void roundTripsPacket() throws Exception {
        ProtocolPacket packet = ProtocolPacket.create(MessageType.CHAT_MESSAGE, "Alice", TargetType.DIRECT, "127.0.0.1", "Xin chào");
        ProtocolPacket decoded = codec.decode(codec.encode(packet), codec.encode(packet).length);
        assertEquals(packet.messageId(), decoded.messageId());
        assertEquals(packet.payload(), decoded.payload());
        assertEquals(packet.timestamp(), decoded.timestamp());
    }

    @Test void rejectsMalformedPacketsAndOversizedData() {
        assertThrows(Exception.class, () -> codec.decode("{broken".getBytes(), 7));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(new byte[PacketCodec.MAX_DATAGRAM_BYTES + 1],
                PacketCodec.MAX_DATAGRAM_BYTES + 1));
    }
}
