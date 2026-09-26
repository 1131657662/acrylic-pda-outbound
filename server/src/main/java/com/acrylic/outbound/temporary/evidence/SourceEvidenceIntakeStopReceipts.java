package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SourceEvidenceIntakeStopReceipts {
    private static final String FIND = """
        SELECT r.receipt_version,r.canonical_request_bytes,r.request_sha256,r.original_response_bytes,
          r.original_response_sha256,r.warehouse_id,r.submission_id,r.work_round,r.creator_device_id,
          r.temporary_authorization_id,r.original_operator_id,r.operation_kind,r.receipt_status,
          r.object_id,r.evidence_set_id,r.evidence_verification_id,o.kind AS object_kind
        FROM source_evidence_intake_receipt r
        LEFT JOIN source_evidence_object o ON o.id=r.object_id
        WHERE r.principal_id=? AND r.route_scope=? AND r.original_key=?
        """;
    private static final String INSERT = """
        INSERT INTO source_evidence_intake_receipt
        (id,receipt_version,principal_id,route_scope,original_key,current_session_id,warehouse_id,scope_id,submission_id,
         work_round,creator_device_id,client_order_id,temporary_authorization_id,original_operator_id,source_recovery_epoch,
         operation_kind,receipt_status,canonical_request_bytes,request_sha256,original_response_bytes,original_response_sha256,
         signature_key_id,signed_receipt,object_id,evidence_set_id,evidence_verification_id,recorded_at)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NULL,NULL,?)
        """;
    private final JdbcTemplate jdbc;

    public SourceEvidenceIntakeStopReceipts(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public SourceEvidenceIntakeReplay.Decision existing(SourceEvidenceIntakeReceipts.BoundKey key, byte[] bytes) {
        return lookup(key, bytes, false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SourceEvidenceIntakeReplay.Decision existingLocked(SourceEvidenceIntakeReceipts.BoundKey key, byte[] bytes) {
        return lookup(key, bytes, true);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID objectIdForLockedReplay(SourceEvidenceIntakeReceipts.BoundKey key, byte[] bytes) {
        if (lookup(key, bytes, true).status() != SourceEvidenceIntakeReplay.Status.REPLAY)
            throw new BusinessException(409, "E2A_STOP_RECEIPT_INTEGRITY_FAILURE", "原停止回执不可核验");
        var rows = jdbc.queryForList(FIND + " FOR UPDATE", Ids.bytes(key.principalId()),
            E2aProtocol.ROUTE_SCOPE, Ids.bytes(key.originalKey()));
        if (rows.size() != 1 || !(rows.getFirst().get("object_id") instanceof byte[] id) || id.length != 16)
            throw new BusinessException(409, "E2A_STOP_RECEIPT_INTEGRITY_FAILURE", "原停止回执不可核验");
        var buffer = ByteBuffer.wrap(id);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW)
    public SourceEvidenceIntakeReplay.Decision recheckAfterRollback(SourceEvidenceIntakeReceipts.BoundKey key,
        byte[] bytes) {
        return lookup(key, bytes, false);
    }

    private SourceEvidenceIntakeReplay.Decision lookup(SourceEvidenceIntakeReceipts.BoundKey key,
        byte[] bytes, boolean locked) {
        if (key == null) throw new IllegalArgumentException("STOP_RECEIPT_KEY");
        var request = SourceEvidenceIntakeReplay.canonicalize(bytes);
        var rows = jdbc.queryForList(locked ? FIND + " FOR UPDATE" : FIND,
            Ids.bytes(key.principalId()), E2aProtocol.ROUTE_SCOPE, Ids.bytes(key.originalKey()));
        if (rows.isEmpty()) return SourceEvidenceIntakeReplay.evaluate(request, null);
        if (rows.size() != 1) return invalid();
        var row = rows.getFirst();
        if (!Arrays.equals(value(row, "original_operator_id"), Ids.bytes(key.principalId()))
            || !"STOP".equals(row.get("operation_kind")) || !"PARTIAL".equals(row.get("receipt_status"))
            || !(row.get("object_id") instanceof byte[] object) || object.length != 16
            || !"STOP_PROOF".equals(row.get("object_kind"))
            || row.get("evidence_set_id") != null || row.get("evidence_verification_id") != null) return invalid();
        if (!Arrays.equals(value(row, "warehouse_id"), Ids.bytes(key.warehouseId()))
            || !Arrays.equals(value(row, "submission_id"), Ids.bytes(key.submissionId()))
            || !(row.get("work_round") instanceof Number round) || round.longValue() != key.workRound()
            || !Arrays.equals(value(row, "creator_device_id"), Ids.bytes(key.creatorDeviceId()))
            || !Arrays.equals(value(row, "temporary_authorization_id"), Ids.bytes(key.authorizationId())))
            return new SourceEvidenceIntakeReplay.Decision(SourceEvidenceIntakeReplay.Status.CONFLICT, null,
                SourceEvidenceIntakeReplay.TrustStatus.NOT_EVALUATED);
        var stored = new SourceEvidenceIntakeReplay.StoredReceipt(version(row.get("receipt_version")),
            value(row, "canonical_request_bytes"), text(row, "request_sha256"),
            value(row, "original_response_bytes"), text(row, "original_response_sha256"));
        return SourceEvidenceIntakeReplay.evaluate(request, stored);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(SourceEvidenceIntakeReceipts.OpenInsert context, UUID objectId,
        SourceEvidenceIntakeReceiptCodec.Receipt receipt) {
        if (context == null || objectId == null || receipt == null
            || !context.principalId().equals(context.originalOperatorId()))
            throw new IllegalArgumentException("STOP_RECEIPT_INSERT");
        if (jdbc.update(INSERT, Ids.bytes(receipt.id()), 1, Ids.bytes(context.principalId()),
            E2aProtocol.ROUTE_SCOPE, Ids.bytes(context.originalKey()), Ids.bytes(context.currentSessionId()),
            Ids.bytes(context.warehouseId()), Ids.bytes(context.scopeId()), Ids.bytes(context.submissionId()),
            context.workRound(), Ids.bytes(context.creatorDeviceId()), Ids.bytes(context.clientOrderId()),
            Ids.bytes(context.authorizationId()), Ids.bytes(context.originalOperatorId()),
            context.sourceRecoveryEpoch(), "STOP", "PARTIAL", context.requestBytes(), context.requestSha256(),
            receipt.originalResponseBytes(), receipt.originalResponseSha256(), receipt.signatureKeyId(),
            receipt.signedReceipt(), Ids.bytes(objectId), Timestamp.from(receipt.recordedAt())) != 1)
            throw new IllegalStateException("STOP_RECEIPT_INSERT_COUNT");
    }

    private static SourceEvidenceIntakeReplay.Decision invalid() {
        return new SourceEvidenceIntakeReplay.Decision(SourceEvidenceIntakeReplay.Status.INTEGRITY_FAILURE, null,
            SourceEvidenceIntakeReplay.TrustStatus.NOT_EVALUATED);
    }

    private static int version(Object raw) { return raw instanceof Number value ? value.intValue() : -1; }
    private static byte[] value(Map<String, Object> row, String key) {
        return row.get(key) instanceof byte[] bytes ? bytes : null;
    }
    private static String text(Map<String, Object> row, String key) {
        return row.get(key) instanceof String value ? value : null;
    }
}
