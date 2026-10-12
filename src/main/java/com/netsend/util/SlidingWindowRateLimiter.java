package com.netsend.util;

import java.time.Duration;
import java.util.ArrayDeque;

public class SlidingWindowRateLimiter {
    private final int limit;
    private final long windowNanos;
    private final ArrayDeque<Long> timestamps = new ArrayDeque<>();

    public SlidingWindowRateLimiter(int limit, Duration window) {
        if (limit < 1 || window.isNegative() || window.isZero()) throw new IllegalArgumentException("Rate limit window is invalid.");
        this.limit = limit;
        this.windowNanos = window.toNanos();
    }

    public synchronized boolean tryAcquire() {
        long now = System.nanoTime();
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= windowNanos) timestamps.removeFirst();
        if (timestamps.size() >= limit) return false;
        timestamps.addLast(now);
        return true;
    }
}
