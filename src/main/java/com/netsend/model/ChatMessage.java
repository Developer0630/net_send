package com.netsend.model;

import java.time.Instant;

public record ChatMessage(String messageId, boolean outgoing, String senderName, String sourceIp,
                          Instant timestamp, TargetType targetType, String targetId, String content, String status) {
}
