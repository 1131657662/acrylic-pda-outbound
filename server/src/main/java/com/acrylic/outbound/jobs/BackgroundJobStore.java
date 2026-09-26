package com.acrylic.outbound.jobs;

import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.*;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
public class BackgroundJobStore {
    static final String SELECT = """
        SELECT BIN_TO_UUID(id),job_type,dedupe_key,status,payload,attempt_count,max_attempts,
          available_at,lease_owner,BIN_TO_UUID(lease_token),lease_until,last_error,created_at,updated_at,recovery_epoch
        FROM background_job
        """;
    private final JdbcTemplate jdbc;
    private final ObjectReader reader;
    private final JobRecoveryFence fence;

    public BackgroundJobStore(JdbcTemplate jdbc, ObjectMapper json, JobRecoveryFence fence) {
        this.jdbc = jdbc;
        this.fence = fence;
        this.reader = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    // Joins the producer's transaction, so a business rollback also removes its job.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BackgroundJob enqueue(NewBackgroundJob input) {
        JsonNode payload = payload(input.payload());
        String epoch = fence.lock();
        jdbc.update("""
            INSERT INTO background_job(id,job_type,dedupe_key,status,payload,max_attempts,available_at,created_at,updated_at,recovery_epoch)
            VALUES(?,?,?,'PENDING',?,?,?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),?)
            ON DUPLICATE KEY UPDATE id=id
            """, Ids.bytes(Ids.next()), input.jobType(), input.dedupeKey(), payload.toString(),
            input.maxAttempts(), Timestamp.from(input.availableAt()), epoch);
        BackgroundJob existing = findByDedupeKey(input.dedupeKey()).orElseThrow();
        if (!epoch.equals(existing.recoveryEpoch()))
            throw new BusinessException(409, "JOB_RECOVERY_EPOCH_MISMATCH", "原任务所属恢复代次不同或未知，保持原任务隔离");
        if (!existing.jobType().equals(input.jobType()) || !payload(existing.payload()).equals(payload)
                || existing.maxAttempts() != input.maxAttempts()) {
            throw new BusinessException(409, "JOB_DEDUPE_CONFLICT", "任务幂等键已绑定其他任务");
        }
        return existing;
    }

    public Optional<BackgroundJob> find(UUID id) {
        return jdbc.query(SELECT + " WHERE id=?", BackgroundJobStore::map, Ids.bytes(id)).stream().findFirst();
    }

    public Optional<BackgroundJob> findByDedupeKey(String key) {
        return jdbc.query(SELECT + " WHERE dedupe_key=?", BackgroundJobStore::map, key).stream().findFirst();
    }

    private JsonNode payload(String value) {
        try {
            JsonNode node = reader.readTree(value);
            if (node == null || !node.isObject()) throw new IllegalArgumentException("Job payload must be a JSON object");
            return node;
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalArgumentException("Invalid job payload JSON");
        }
    }

    static BackgroundJob map(ResultSet rs, int ignored) throws SQLException {
        String token = rs.getString(10);
        return new BackgroundJob(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3),
            BackgroundJob.Status.valueOf(rs.getString(4)), rs.getString(5), rs.getInt(6), rs.getInt(7),
            rs.getTimestamp(8).toInstant(), rs.getString(9), token == null ? null : UUID.fromString(token),
            rs.getTimestamp(11) == null ? null : rs.getTimestamp(11).toInstant(), rs.getString(12),
            rs.getTimestamp(13).toInstant(), rs.getTimestamp(14).toInstant(), rs.getString(15));
    }
}
