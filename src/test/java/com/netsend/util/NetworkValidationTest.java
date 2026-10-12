package com.netsend.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NetworkValidationTest {
    @Test void acceptsValidIpv4AndRejectsInvalidAddress() {
        assertEquals("192.168.1.20", NetworkValidation.ipv4("192.168.1.20").getHostAddress());
        assertThrows(IllegalArgumentException.class, () -> NetworkValidation.ipv4("999.1.1.1"));
        assertThrows(IllegalArgumentException.class, () -> NetworkValidation.ipv4("example.com"));
        assertThrows(IllegalArgumentException.class, () -> NetworkValidation.ipv4("192.168.01.20"));
    }

    @Test void validatesPortsGroupsAndMessageLimits() {
        NetworkValidation.port(65535);
        assertThrows(IllegalArgumentException.class, () -> NetworkValidation.port(0));
        NetworkValidation.multicastAddress("239.10.10.10");
        assertThrows(IllegalArgumentException.class, () -> NetworkValidation.multicastAddress("192.168.1.1"));
        assertThrows(IllegalArgumentException.class, () -> NetworkValidation.multicastAddress("224.0.0.5"));
        assertThrows(IllegalArgumentException.class, () -> NetworkValidation.message("  "));
        assertThrows(IllegalArgumentException.class, () -> NetworkValidation.message("a".repeat(1001)));
    }
}
