package com.acrylic.outbound.jobs;

public interface BackgroundJobHandler {
    String jobType();

    /**
     * Prepare outside a transaction; renew the lease during long work. Return only short DB writes,
     * which the worker publishes atomically with its fence. Files must use a per-token staging path;
     * external notifications require their own stable event key, since execution is at least once.
     */
    Runnable execute(BackgroundJobExecution execution) throws Exception;
}
