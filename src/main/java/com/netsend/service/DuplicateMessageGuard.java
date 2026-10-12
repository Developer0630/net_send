package com.netsend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public class DuplicateMessageGuard {
    private final ConcurrentHashMap<String, Instant> seen = new ConcurrentHashMap<>();
    private final Duration retention;

    public DuplicateMessageGuard(Duration retention) { this.retention = retention; }
    public boolean firstSeen(String id) {
        Instant now = Instant.now();
        seen.entrySet().removeIf(entry -> entry.getValue().isBefore(now.minus(retention)));
        return seen.putIfAbsent(id, now) == null;
    }
}
