package com.netsend.model;

import java.time.Instant;

public record Device(String name, String ip, int listenPort, Instant lastSeen) {
    public boolean online() {
        return lastSeen != null && lastSeen.isAfter(Instant.now().minusSeconds(45));
    }
}
