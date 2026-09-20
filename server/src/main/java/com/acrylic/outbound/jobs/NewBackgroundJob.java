package com.acrylic.outbound.jobs;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

public record NewBackgroundJob(String jobType, String dedupeKey, String payload, int maxAttempts, Instant availableAt) {
    public NewBackgroundJob {
        if (jobType == null || !jobType.matches("[A-Z][A-Z0-9_.:-]{0,79}"))
            throw new IllegalArgumentException("Invalid job type");
        if (dedupeKey == null || !dedupeKey.matches("[!-~]{1,180}"))
            throw new IllegalArgumentException("Invalid job dedupe key");
        if (payload == null || payload.getBytes(StandardCharsets.UTF_8).length > 65536)
            throw new IllegalArgumentException("Job payload must be at most 64 KiB; use immutable references for large data");
        if (maxAttempts < 1 || maxAttempts > 20) throw new IllegalArgumentException("Invalid max attempts");
        if (availableAt == null) throw new IllegalArgumentException("Job schedule is required");
    }
}
