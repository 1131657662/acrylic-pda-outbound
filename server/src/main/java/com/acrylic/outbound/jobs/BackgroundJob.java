package com.acrylic.outbound.jobs;

import java.time.Instant;
import java.util.UUID;

public record BackgroundJob(
    UUID id,
    String jobType,
    String dedupeKey,
    Status status,
    String payload,
    int attemptCount,
    int maxAttempts,
    Instant availableAt,
    String leaseOwner,
    UUID leaseToken,
    Instant leaseUntil,
    String lastError,
    Instant createdAt,
    Instant updatedAt
) {
    public enum Status { PENDING, RUNNING, RETRY, SUCCEEDED, FAILED }
}
