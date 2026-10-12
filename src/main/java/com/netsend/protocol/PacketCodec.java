package com.netsend.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netsend.model.ProtocolPacket;
import java.io.IOException;

public class PacketCodec {
    public static final int MAX_DATAGRAM_BYTES = 8192;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public byte[] encode(ProtocolPacket packet) throws IOException {
        validate(packet);
        byte[] data = mapper.writeValueAsBytes(packet);
        if (data.length > MAX_DATAGRAM_BYTES) throw new IllegalArgumentException("Gói tin vượt quá giới hạn 8192 byte.");
        return data;
    }

    public ProtocolPacket decode(byte[] data, int length) throws IOException {
        if (data == null || length <= 0 || length > MAX_DATAGRAM_BYTES || length > data.length)
            throw new IllegalArgumentException("Kích thước datagram không hợp lệ.");
        ProtocolPacket packet = mapper.readValue(data, 0, length, ProtocolPacket.class);
        validate(packet);
        return packet;
    }

    private void validate(ProtocolPacket packet) {
        if (packet == null || packet.protocolVersion() != 1 || packet.messageId() == null || packet.messageId().isBlank()
                || packet.senderInstanceId() == null || packet.senderInstanceId().isBlank()
                || packet.messageType() == null || packet.targetType() == null
                || packet.senderName() == null || packet.senderName().isBlank()
                || packet.timestamp() == null || packet.payload() == null)
            throw new IllegalArgumentException("Cấu trúc gói tin thiếu trường bắt buộc hoặc không hỗ trợ.");
        if (packet.senderName().length() > 80 || packet.payload().length() > 1000)
            throw new IllegalArgumentException("Tên người gửi hoặc nội dung vượt giới hạn.");
    }
}
