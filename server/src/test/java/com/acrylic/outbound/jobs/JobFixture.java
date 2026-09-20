package com.acrylic.outbound.jobs;

import com.acrylic.outbound.common.Ids;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

final class JobFixture implements AutoCloseable {
    final String type = "IT." + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
    final BackgroundJobStore jobs;
    final BackgroundJobLeases leases;
    private final JdbcTemplate jdbc;

    JobFixture(BackgroundJobStore jobs, BackgroundJobLeases leases, JdbcTemplate jdbc) {
        this.jobs = jobs; this.leases = leases; this.jdbc = jdbc;
    }

    NewBackgroundJob input(int maxAttempts) {
        return new NewBackgroundJob(type, type + ":" + UUID.randomUUID(), "{}", maxAttempts, Instant.EPOCH);
    }

    BackgroundJob enqueue(int maxAttempts) { return jobs.enqueue(input(maxAttempts)); }

    BackgroundJob claim() { return leases.claim("worker", Set.of(type), Duration.ofMinutes(1)).orElseThrow(); }

    BackgroundJob current(BackgroundJob job) { return jobs.find(job.id()).orElseThrow(); }

    void expire(BackgroundJob job) {
        jdbc.update("UPDATE background_job SET lease_until=UTC_TIMESTAMP(3)-INTERVAL 1 SECOND WHERE id=?", Ids.bytes(job.id()));
    }

    void ready(BackgroundJob job) {
        jdbc.update("UPDATE background_job SET available_at=UTC_TIMESTAMP(3)-INTERVAL 1 SECOND WHERE id=?", Ids.bytes(job.id()));
    }

    BackgroundJobWorker worker(Prepare prepare) {
        return new BackgroundJobWorker(leases, List.of(new BackgroundJobHandler() {
            public String jobType() { return type; }
            public Runnable execute(BackgroundJobExecution execution) throws Exception { return prepare.run(execution); }
        }));
    }

    @Override public void close() { jdbc.update("DELETE FROM background_job WHERE job_type=?", type); }

    @FunctionalInterface interface Prepare { Runnable run(BackgroundJobExecution execution) throws Exception; }
}
