package com.acrylic.outbound.jobs;

import com.acrylic.outbound.common.Ids;
import java.time.Duration;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
@Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW)
public class BackgroundJobLeases {
    private static final String LIVE = " WHERE id=? AND status='RUNNING' AND lease_owner=? AND lease_token=? AND recovery_epoch=? AND lease_until>UTC_TIMESTAMP(3)";
    private final JdbcTemplate jdbc;
    private final JobRecoveryFence fence;

    public BackgroundJobLeases(JdbcTemplate jdbc, JobRecoveryFence fence) { this.jdbc = jdbc; this.fence = fence; }

    public Optional<BackgroundJob> claim(String owner, Set<String> types, Duration lease) {
        if (owner == null || !owner.matches("[!-~]{1,120}")) throw new IllegalArgumentException("Invalid worker owner");
        long millis = leaseMillis(lease);
        if (types.isEmpty()) return Optional.empty();
        String epoch = fence.lock();
        var rows = jdbc.query(BackgroundJobStore.SELECT + " WHERE job_type IN (" + marks(types) + ")" + """
            AND recovery_epoch=? AND status IN ('PENDING','RETRY') AND attempt_count<max_attempts AND available_at<=UTC_TIMESTAMP(3)
            ORDER BY available_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
            """, BackgroundJobStore::map, parameters(types, epoch));
        if (rows.isEmpty()) return Optional.empty();
        byte[] id = Ids.bytes(rows.getFirst().id());
        jdbc.update("""
            UPDATE background_job SET status='RUNNING',attempt_count=attempt_count+1,
              lease_owner=?,lease_token=?,lease_until=TIMESTAMPADD(MICROSECOND,?,UTC_TIMESTAMP(3)),updated_at=UTC_TIMESTAMP(3)
            WHERE id=?
            """, owner, Ids.bytes(Ids.next()), millis * 1000, id);
        return jdbc.query(BackgroundJobStore.SELECT + " WHERE id=?", BackgroundJobStore::map, id).stream().findFirst();
    }

    public int recoverExpired(Set<String> types) {
        if (types.isEmpty()) return 0;
        String epoch = fence.lock();
        var rows = jdbc.query(BackgroundJobStore.SELECT + " WHERE job_type IN (" + marks(types) + ")" + """
            AND recovery_epoch=? AND status='RUNNING' AND lease_until<=UTC_TIMESTAMP(3)
            ORDER BY lease_until,id LIMIT 100 FOR UPDATE SKIP LOCKED
            """, BackgroundJobStore::map, parameters(types, epoch));
        for (BackgroundJob job : rows) {
            jdbc.update("""
                UPDATE background_job SET status=IF(attempt_count>=max_attempts,'FAILED','RETRY'),
                  available_at=TIMESTAMPADD(MICROSECOND,?,UTC_TIMESTAMP(3)),last_error='LEASE_EXPIRED',
                  lease_owner=NULL,lease_token=NULL,lease_until=NULL,updated_at=UTC_TIMESTAMP(3) WHERE id=?
                """, RetryBackoff.forAttempt(job.attemptCount()).toMillis() * 1000, Ids.bytes(job.id()));
        }
        return rows.size();
    }

    public boolean renew(BackgroundJob job, Duration lease) {
        long millis = leaseMillis(lease);
        if (!lockOwner(job)) return false;
        return jdbc.update("""
            UPDATE background_job SET lease_until=TIMESTAMPADD(MICROSECOND,?,UTC_TIMESTAMP(3)),updated_at=UTC_TIMESTAMP(3)
            """ + LIVE, millis * 1000, Ids.bytes(job.id()), job.leaseOwner(), Ids.bytes(job.leaseToken()), job.recoveryEpoch()) == 1;
    }

    // Only short database publication belongs here. The fence and result share one commit/rollback.
    public boolean complete(BackgroundJob job, Runnable publishResult) {
        if (!lockOwner(job)) return false;
        int changed = jdbc.update("""
            UPDATE background_job SET status='SUCCEEDED',lease_owner=NULL,lease_token=NULL,lease_until=NULL,
              last_error=NULL,updated_at=UTC_TIMESTAMP(3)
            """ + LIVE, Ids.bytes(job.id()), job.leaseOwner(), Ids.bytes(job.leaseToken()), job.recoveryEpoch());
        if (changed == 0) return false;
        Objects.requireNonNull(publishResult, "Job result publication is required").run();
        return true;
    }

    public Outcome fail(BackgroundJob job, JobFailure error) {
        if (!lockOwner(job)) return Outcome.STALE;
        var rows = jdbc.query(BackgroundJobStore.SELECT + LIVE + " FOR UPDATE", BackgroundJobStore::map,
            Ids.bytes(job.id()), job.leaseOwner(), Ids.bytes(job.leaseToken()), job.recoveryEpoch());
        if (rows.isEmpty()) return Outcome.STALE;
        BackgroundJob current = rows.getFirst();
        boolean exhausted = !error.retryable() || current.attemptCount() >= current.maxAttempts();
        int changed = jdbc.update("""
            UPDATE background_job SET status=?,available_at=TIMESTAMPADD(MICROSECOND,?,UTC_TIMESTAMP(3)),
              lease_owner=NULL,lease_token=NULL,lease_until=NULL,last_error=?,updated_at=UTC_TIMESTAMP(3)
            """ + LIVE, exhausted ? "FAILED" : "RETRY", RetryBackoff.forAttempt(current.attemptCount()).toMillis() * 1000,
            error.getMessage(), Ids.bytes(current.id()), current.leaseOwner(), Ids.bytes(current.leaseToken()), current.recoveryEpoch());
        if (changed == 0) return Outcome.STALE;
        return exhausted ? Outcome.FAILED : Outcome.RETRY_SCHEDULED;
    }

    private String marks(Set<String> types) {
        if (types.size() > 32) throw new IllegalArgumentException("At most 32 job handler types per worker");
        return String.join(",", Collections.nCopies(types.size(), "?"));
    }

    private boolean lockOwner(BackgroundJob job) {
        if (!fence.lock().equals(job.recoveryEpoch())) return false;
        // Acquire the row first: MySQL UTC_TIMESTAMP is fixed at statement start, before any lock wait.
        return !jdbc.queryForList("""
            SELECT id FROM background_job WHERE id=? AND status='RUNNING' AND lease_owner=? AND lease_token=? AND recovery_epoch=? FOR UPDATE
            """, Ids.bytes(job.id()), job.leaseOwner(), Ids.bytes(job.leaseToken()), job.recoveryEpoch()).isEmpty();
    }

    private Object[] parameters(Set<String> types, String epoch) {
        var parameters = new ArrayList<Object>(types); parameters.add(epoch); return parameters.toArray();
    }

    private long leaseMillis(Duration lease) {
        if (lease == null || lease.compareTo(Duration.ofSeconds(1)) < 0 || lease.compareTo(Duration.ofMinutes(10)) > 0)
            throw new IllegalArgumentException("Lease must be between 1 second and 10 minutes");
        return lease.toMillis();
    }

    public enum Outcome { RETRY_SCHEDULED, FAILED, STALE }
}
