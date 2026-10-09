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

/** Explicit original NORMAL read-only snapshot of an unsubmitted first-round TEMP source. */
public final class RecoveryUnsubmittedOccupancyCapture {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public RecoveryUnsubmittedOccupancyCapture(DataSource database) {
        jdbc = new JdbcTemplate(Objects.requireNonNull(database));
        transaction = new TransactionTemplate(new DataSourceTransactionManager(database));
        transaction.setReadOnly(true); transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public Observation capture(Request request) {
        require(request != null && !TransactionSynchronizationManager.isActualTransactionActive(), "RECOVERY_UNSUBMITTED_TRANSACTION");
        var expected = RecoveryUnsubmittedEvidence.bind(request.identity(), request.originalPayload(), request.evidence());
        return transaction.execute(status -> {
            var value = state(request.database()); var payload = SubmissionJson.parse(request.originalPayload());
            RecoveryOriginalOccupancyFacts.payload(payload, request.identity());
            String receipt = head(request.identity());
            var set = new RecoveryUnsubmittedEvidenceRows(jdbc).read(request.identity(), expected);
            var occupancy = SubmissionJson.object().put("formatVersion", 1).put("payloadSha256", SubmissionJson.sha(payload))
                .put("sourceEvidenceSetId", text(set, "id"));
            occupancy.set("source", RecoveryOriginalOccupancyFacts.source(request.identity()));
            occupancy.set("rows", new RecoveryUnsubmittedOccupancyRows(jdbc).read(request.identity(), payload, uuid(set, "id")));
            value.set("source", occupancy.path("source")); value.set("occupancy", occupancy); value.set("evidenceSet", set);
            value.put("payloadSha256", SubmissionJson.sha(payload)).put("terminalReceipt", receipt);
            byte[] bytes = RecoveryControlJson.encode(value); return new Observation(bytes, sha256(bytes), instant(value, "observedAt"));
        });
    }

    private ObjectNode state(RecoveryDeviceRegistrationCapture.Database database) {
        var rows = jdbc.query("SELECT @@server_uuid,DATABASE(),recovery_epoch,service_mode,UTC_TIMESTAMP(3) FROM server_state WHERE id=1",
            (rs, index) -> SubmissionJson.object().put("formatVersion", 1).put("observationKind", "ORIGINAL_UNSUBMITTED_TEMP_OCCUPANCY")
                .put("mysqlServerUuid", rs.getString(1)).put("databaseSchema", rs.getString(2)).put("sourceRecoveryEpoch", rs.getString(3))
                .put("serviceMode", rs.getString(4)).put("observedAt", rs.getTimestamp(5).toInstant().toString()));
        require(rows.size() == 1, "RECOVERY_UNSUBMITTED_STATE"); var value = rows.getFirst();
        require(uuid(value, "mysqlServerUuid").equals(database.mysqlServerUuid()) && text(value, "databaseSchema").equals(database.schema())
            && text(value, "sourceRecoveryEpoch").equals(database.sourceRecoveryEpoch()) && text(value, "serviceMode").equals("NORMAL"),
            "RECOVERY_UNSUBMITTED_DATABASE"); value.remove("serviceMode"); return value;
    }

    private String head(RecoverySourceIdentity id) {
        var rows = jdbc.query("""
            SELECT BIN_TO_UUID(s.scope_id) scopeId,BIN_TO_UUID(c.warehouse_id) warehouseId,
             BIN_TO_UUID(s.freeze_device_id) deviceId,BIN_TO_UUID(s.operator_id) operatorId,BIN_TO_UUID(s.temporary_authorization_id) qualificationId,
             BIN_TO_UUID(c.canonical_order_id) canonicalOrderId,BIN_TO_UUID(c.creator_device_id) creatorDeviceId,BIN_TO_UUID(c.client_order_id) clientOrderId,
             CAST(s.work_round AS CHAR) workRound,CAST(s.source_recovery_epoch AS CHAR) sourceRecoveryEpoch,s.status,s.receipt_text,
             BIN_TO_UUID(c.current_submission_id) currentSubmissionId,CAST(c.work_round AS CHAR) scopeRound,BIN_TO_UUID(s.assignment_id) assignmentId,
             BIN_TO_UUID(c.source_order_id) sourceOrderId,BIN_TO_UUID(s.continuation_id) continuationId,BIN_TO_UUID(s.superseded_by) supersededBy,
             BIN_TO_UUID(p.submission_id) receivedPayload
            FROM submission s JOIN submission_scope c ON c.id=s.scope_id LEFT JOIN submission_payload p ON p.submission_id=s.id WHERE s.id=?
            """, (rs, index) -> {
                var value = SubmissionJson.object();
                for (String field : List.of("scopeId", "warehouseId", "deviceId", "operatorId", "qualificationId", "canonicalOrderId",
                        "creatorDeviceId", "clientOrderId", "workRound", "sourceRecoveryEpoch", "status", "receipt_text", "currentSubmissionId",
                        "scopeRound", "assignmentId", "sourceOrderId", "continuationId", "supersededBy", "receivedPayload")) value.put(field, rs.getString(field));
                return value;
            }, Ids.bytes(id.submissionId()));
        require(rows.size() == 1, "RECOVERY_UNSUBMITTED_UNKNOWN"); return requireHead(rows.getFirst(), id);
    }

    private String requireHead(ObjectNode head, RecoverySourceIdentity id) {
        var source = RecoveryOriginalOccupancyFacts.source(id);
        for (String field : List.of("scopeId", "warehouseId", "deviceId", "operatorId", "qualificationId", "canonicalOrderId",
                "creatorDeviceId", "clientOrderId", "workRound", "sourceRecoveryEpoch"))
            require(head.path(field).equals(source.path(field)), "RECOVERY_UNSUBMITTED_HEAD_SOURCE");
        require(uuid(head, "currentSubmissionId").equals(id.submissionId()) && revision(head, "scopeRound") == 1, "RECOVERY_UNSUBMITTED_HEAD_SOURCE");
        for (String field : List.of("assignmentId", "sourceOrderId", "continuationId", "supersededBy", "receivedPayload"))
            require(head.path(field).isNull(), "RECOVERY_UNSUBMITTED_HEAD_CHAIN");
        require(text(head, "status").equals("ABORTED"), "RECOVERY_UNSUBMITTED_HEAD_TERMINAL");
        String receipt = text(head, "receipt_text"); var value = SubmissionJson.parse(receipt);
        require(value.path("payloadSha256").isNull() && value.path("payloadPresent").isBoolean() && !value.path("payloadPresent").booleanValue(),
            "RECOVERY_UNSUBMITTED_HEAD_PAYLOAD"); return receipt;
    }

    public record Request(RecoveryDeviceRegistrationCapture.Database database, RecoverySourceIdentity identity,
            byte[] originalPayload, RecoveryOriginalEvidence.Request evidence) {
        public Request {
            require(database != null && identity != null && evidence != null && identity.sourceKind().equals("TEMP") && identity.workRound() == 1
                && originalPayload != null && originalPayload.length > 0 && originalPayload.length <= 33554432, "RECOVERY_UNSUBMITTED_REQUEST");
            require(database.sourceRecoveryEpoch().equals(identity.sourceRecoveryEpoch()), "RECOVERY_UNSUBMITTED_EPOCH"); originalPayload = originalPayload.clone();
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
