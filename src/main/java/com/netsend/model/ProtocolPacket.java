package com.netsend.model;

import java.time.Instant;
import java.util.UUID;

public record ProtocolPacket(
        int protocolVersion,
        String messageId,
        String senderInstanceId,
        MessageType messageType,
        String senderName,
        Instant timestamp,
        TargetType targetType,
        String targetId,
        String payload) {

    private static final String DEFAULT_INSTANCE_ID = UUID.randomUUID().toString();

    public static ProtocolPacket create(MessageType type, String sender, TargetType target, String targetId, String payload) {
        return create(type, sender, DEFAULT_INSTANCE_ID, target, targetId, payload);
    }

    public static ProtocolPacket create(MessageType type, String sender, String instanceId, TargetType target, String targetId, String payload) {
        return new ProtocolPacket(1, UUID.randomUUID().toString(), instanceId, type, sender, Instant.now(), target, targetId, payload);
    }
}
