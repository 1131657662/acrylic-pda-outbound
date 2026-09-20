package com.acrylic.outbound.jobs;

import java.time.Duration;

public final class RetryBackoff {
    private static final Duration BASE = Duration.ofSeconds(5);
    private static final Duration MAX = Duration.ofMinutes(15);

    private RetryBackoff() { }

    public static Duration forAttempt(int attemptCount) {
        if (attemptCount < 1) throw new IllegalArgumentException("attemptCount must be positive");
        long multiplier = 1L << Math.min(attemptCount - 1, 10);
        Duration delay = BASE.multipliedBy(multiplier);
        return delay.compareTo(MAX) > 0 ? MAX : delay;
    }
}
