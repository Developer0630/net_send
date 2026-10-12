package com.netsend.service;

import java.time.Duration;
import com.netsend.util.SlidingWindowRateLimiter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DuplicateMessageGuardTest {
    @Test void onlyAcceptsFirstMessageIdDuringRetentionWindow() {
        DuplicateMessageGuard guard = new DuplicateMessageGuard(Duration.ofMinutes(2));
        assertTrue(guard.firstSeen("same-id"));
        assertFalse(guard.firstSeen("same-id"));
        assertTrue(guard.firstSeen("other-id"));
    }

    @Test void enforcesSlidingWindowRateLimit() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(2, Duration.ofSeconds(1));
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
    }
}
