package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Pre-failure committed snapshot, not a historical window or a reservation on the restored database. */
public final class RecoveryOriginalOccupancyCapture {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public RecoveryOriginalOccupancyCapture(DataSource database) {
        jdbc = new JdbcTemplate(Objects.requireNonNull(database));
        transaction = new TransactionTemplate(new DataSourceTransactionManager(database));
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public Observation capture(Request request) {
        require(request != null && !TransactionSynchronizationManager.isActualTransactionActive(), "RECOVERY_OCCUPANCY_CAPTURE_TRANSACTION");
        return transaction.execute(status -> {
            var value = state(request.database());
            var payload = SubmissionJson.parse(request.originalPayload());
            RecoveryOriginalOccupancyFacts.payload(payload, request.identity());
            var original = head(request, SubmissionJson.sha(payload));
            var material = SubmissionJson.object().put("formatVersion", 1).put("payloadSha256", SubmissionJson.sha(payload))
                .putNull("sourceEvidenceSetId");
            material.set("source", RecoveryOriginalOccupancyFacts.source(request.identity()));
            material.set("rows", new RecoveryOriginalOccupancyRows(jdbc).read(request.identity(), payload));
            value.set("source", material.path("source")); value.set("occupancy", material);
            value.put("payloadSha256", SubmissionJson.sha(payload)).put("terminalReceipt", original);
            byte[] bytes = RecoveryControlJson.encode(value);
            return new Observation(bytes, sha256(bytes), instant(value, "observedAt"));
        });
    }

    private ObjectNode state(RecoveryDeviceRegistrationCapture.Database expected) {
        var rows = jdbc.query("SELECT @@server_uuid,DATABASE(),recovery_epoch,service_mode,UTC_TIMESTAMP(3) FROM server_state WHERE id=1",
            (rs, index) -> SubmissionJson.object().put("formatVersion", 1).put("observationKind", "ORIGINAL_SUBMISSION_OCCUPANCY")
                .put("mysqlServerUuid", rs.getString(1)).put("databaseSchema", rs.getString(2)).put("sourceRecoveryEpoch", rs.getString(3))
                .put("serviceMode", rs.getString(4)).put("observedAt", rs.getTimestamp(5).toInstant().toString()));
        require(rows.size() == 1, "RECOVERY_OCCUPANCY_CAPTURE_STATE"); var value = rows.getFirst();
        require(uuid(value, "mysqlServerUuid").equals(expected.mysqlServerUuid()) && text(value, "databaseSchema").equals(expected.schema())
            && text(value, "sourceRecoveryEpoch").equals(expected.sourceRecoveryEpoch())
            && "NORMAL".equals(text(value, "serviceMode")), "RECOVERY_OCCUPANCY_CAPTURE_DATABASE");
        value.remove("serviceMode"); return value;
    }

    private String head(Request request, String payloadHash) {
        var rows = jdbc.query("""
            SELECT BIN_TO_UUID(s.scope_id) scopeId,BIN_TO_UUID(c.warehouse_id) warehouseId,
             BIN_TO_UUID(s.freeze_device_id) deviceId,BIN_TO_UUID(s.operator_id) operatorId,
             BIN_TO_UUID(COALESCE(s.assignment_id,s.temporary_authorization_id)) qualificationId,
             BIN_TO_UUID(COALESCE(c.source_order_id,c.canonical_order_id)) canonicalOrderId,
             BIN_TO_UUID(c.creator_device_id) creatorDeviceId,BIN_TO_UUID(c.client_order_id) clientOrderId,
             CAST(s.work_round AS CHAR) workRound,CAST(s.source_recovery_epoch AS CHAR) sourceRecoveryEpoch,
             s.status,s.receipt_text,p.payload_sha256,BIN_TO_UUID(c.current_submission_id) currentSubmissionId,
             CAST(c.work_round AS CHAR) scopeRound,BIN_TO_UUID(s.continuation_id) continuationId,
             BIN_TO_UUID(s.superseded_by) supersededBy
            FROM submission s JOIN submission_scope c ON c.id=s.scope_id
            LEFT JOIN submission_payload p ON p.submission_id=s.id WHERE s.id=?
            """, (rs, index) -> {
                var value = SubmissionJson.object();
                for (String field : List.of("scopeId", "warehouseId", "deviceId", "operatorId", "qualificationId", "canonicalOrderId",
                        "creatorDeviceId", "clientOrderId", "workRound", "sourceRecoveryEpoch", "status", "receipt_text", "payload_sha256",
                        "currentSubmissionId", "scopeRound", "continuationId", "supersededBy")) value.put(field, rs.getString(field));
                return value;
            }, Ids.bytes(request.identity().submissionId()));
        require(rows.size() == 1, "RECOVERY_OCCUPANCY_CAPTURE_UNKNOWN");
        return RecoveryOriginalOccupancyFacts.head(rows.getFirst(), request.identity(), payloadHash);
    }

    public record Request(RecoveryDeviceRegistrationCapture.Database database, RecoverySourceIdentity identity, byte[] originalPayload) {
        public Request {
            require(database != null && identity != null && identity.workRound() == 1 && originalPayload != null
                && originalPayload.length > 0 && originalPayload.length <= 33554432, "RECOVERY_OCCUPANCY_CAPTURE_REQUEST");
            require(database.sourceRecoveryEpoch().equals(identity.sourceRecoveryEpoch()), "RECOVERY_OCCUPANCY_CAPTURE_EPOCH");
            originalPayload = originalPayload.clone();
        }
        @Override public byte[] originalPayload() { return originalPayload.clone(); }
    }
    public record Observation(byte[] originalBytes, String originalSha256, Instant observedAt) {
        public Observation { originalBytes = originalBytes.clone(); }
        @Override public byte[] originalBytes() { return originalBytes.clone(); }
        public boolean ordinaryWritesAllowed() { return false; }
        public boolean localDeletionAllowed() { return false; }
    }
}
