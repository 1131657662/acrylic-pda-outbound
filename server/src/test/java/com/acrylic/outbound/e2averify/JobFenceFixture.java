package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.jobs.*;
import java.time.*;
import java.util.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

final class JobFenceFixture {
    final BackgroundJobStore jobs;
    final BackgroundJobLeases leases;
    final JdbcTemplate jdbc;
    final ServerState state;
    final PlatformTransactionManager transactions;
    final String type = "FENCE." + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);

    JobFenceFixture(ConfigurableApplicationContext context) {
        jobs = context.getBean(BackgroundJobStore.class); leases = context.getBean(BackgroundJobLeases.class);
        jdbc = context.getBean(JdbcTemplate.class); state = context.getBean(ServerState.class);
        transactions = context.getBean(PlatformTransactionManager.class);
    }

    NewBackgroundJob input() { return new NewBackgroundJob(type, "fence:" + UUID.randomUUID(), "{}", 3, Instant.EPOCH); }
    BackgroundJob enqueue() { return jobs.enqueue(input()); }
    BackgroundJob claim() { return leases.claim("fence-worker", Set.of(type), Duration.ofMinutes(1)).orElseThrow(); }
    BackgroundJob current(BackgroundJob job) { return jobs.find(job.id()).orElseThrow(); }
    TransactionTemplate tx() { return new TransactionTemplate(transactions); }

    void expire(BackgroundJob job) {
        jdbc.update("UPDATE background_job SET lease_until=UTC_TIMESTAMP(3)-INTERVAL 1 SECOND WHERE id=?", Ids.bytes(job.id()));
    }

    void state(String epoch, String mode) {
        jdbc.update("UPDATE server_state SET recovery_epoch=?,service_mode=? WHERE id=1", epoch, mode);
    }

    static BackgroundJob withEpoch(BackgroundJob job, String epoch) {
        return new BackgroundJob(job.id(), job.jobType(), job.dedupeKey(), job.status(), job.payload(),
            job.attemptCount(), job.maxAttempts(), job.availableAt(), job.leaseOwner(), job.leaseToken(),
            job.leaseUntil(), job.lastError(), job.createdAt(), job.updatedAt(), epoch);
    }
}
