package com.acrylic.outbound.jobs;

import java.time.Duration;

public final class BackgroundJobExecution {
    static final Duration LEASE = Duration.ofMinutes(2);
    private final BackgroundJob job;
    private final BackgroundJobLeases leases;

    BackgroundJobExecution(BackgroundJob job, BackgroundJobLeases leases) { this.job = job; this.leases = leases; }

    public BackgroundJob job() { return job; }

    /** Call before expiry and before continuing expensive work. A lost lease must stop this attempt. */
    public void renew() throws JobFailure {
        if (!leases.renew(job, LEASE)) throw new JobFailure("LEASE_LOST", false);
    }
}
