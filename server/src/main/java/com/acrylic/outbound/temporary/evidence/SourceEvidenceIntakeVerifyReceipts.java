package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SourceEvidenceIntakeVerifyReceipts {
    private static final String FIND = """
        SELECT receipt_version,canonical_request_bytes,request_sha256,original_response_bytes,original_response_sha256,
          warehouse_id,submission_id,work_round,creator_device_id,temporary_authorization_id,original_operator_id,
          operation_kind,receipt_status,object_id,evidence_set_id,evidence_verification_id
        FROM source_evidence_intake_receipt
        WHERE principal_id=? AND route_scope=? AND original_key=?
        """;
    private static final String INSERT = """
        INSERT INTO source_evidence_intake_receipt
        (id,receipt_version,principal_id,route_scope,original_key,current_session_id,warehouse_id,scope_id,submission_id,
         work_round,creator_device_id,client_order_id,temporary_authorization_id,original_operator_id,source_recovery_epoch,
         operation_kind,receipt_status,canonical_request_bytes,request_sha256,original_response_bytes,original_response_sha256,
         signature_key_id,signed_receipt,object_id,evidence_set_id,evidence_verification_id,recorded_at)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NULL,?, ?,?)
        """;
    private final JdbcTemplate jdbc;

    public SourceEvidenceIntakeVerifyReceipts(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public SourceEvidenceIntakeReplay.Decision existing(SourceEvidenceIntakeReceipts.BoundKey key, byte[] bytes) {
        return lookup(key, bytes, false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SourceEvidenceIntakeReplay.Decision existingLocked(SourceEvidenceIntakeReceipts.BoundKey key, byte[] bytes) {
        return lookup(key, bytes, true);
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW)
    public SourceEvidenceIntakeReplay.Decision recheckAfterRollback(SourceEvidenceIntakeReceipts.BoundKey key,
        byte[] bytes) { return lookup(key, bytes, false); }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(SourceEvidenceIntakeReceipts.OpenInsert context, UUID setId, UUID verificationId,
        SourceEvidenceIntakeReceiptCodec.Receipt receipt) {
        if (context == null || setId == null || verificationId == null || receipt == null
            || !context.principalId().equals(context.originalOperatorId()))
            throw new IllegalArgumentException("VERIFY_RECEIPT_INSERT");
        if (jdbc.update(INSERT, Ids.bytes(receipt.id()), 1, Ids.bytes(context.principalId()), E2aProtocol.ROUTE_SCOPE,
            Ids.bytes(context.originalKey()), Ids.bytes(context.currentSessionId()), Ids.bytes(context.warehouseId()),
            Ids.bytes(context.scopeId()), Ids.bytes(context.submissionId()), context.workRound(),
            Ids.bytes(context.creatorDeviceId()), Ids.bytes(context.clientOrderId()), Ids.bytes(context.authorizationId()),
            Ids.bytes(context.originalOperatorId()), context.sourceRecoveryEpoch(), "VERIFY", status(receipt.originalResponseBytes()),
            context.requestBytes(), context.requestSha256(), receipt.originalResponseBytes(), receipt.originalResponseSha256(),
            receipt.signatureKeyId(), receipt.signedReceipt(), Ids.bytes(setId), Ids.bytes(verificationId),
            Timestamp.from(receipt.recordedAt())) != 1) throw new IllegalStateException("VERIFY_RECEIPT_INSERT_COUNT");
    }

    private SourceEvidenceIntakeReplay.Decision lookup(SourceEvidenceIntakeReceipts.BoundKey key, byte[] bytes,
        boolean locked) {
        if (key == null) throw new IllegalArgumentException("VERIFY_RECEIPT_KEY");
        var request = SourceEvidenceIntakeReplay.canonicalize(bytes);
        var rows = jdbc.queryForList(locked ? FIND + " FOR UPDATE" : FIND, Ids.bytes(key.principalId()),
            E2aProtocol.ROUTE_SCOPE, Ids.bytes(key.originalKey()));
        if (rows.isEmpty()) return SourceEvidenceIntakeReplay.evaluate(request, null);
        if (rows.size() != 1) return invalid();
        var row = rows.getFirst();
        if (!Arrays.equals(value(row, "original_operator_id"), Ids.bytes(key.principalId()))
            || !"VERIFY".equals(row.get("operation_kind")) || !matchingStatus(row)
            || row.get("object_id") != null || !(row.get("evidence_set_id") instanceof byte[] set)
            || set.length != 16 || !(row.get("evidence_verification_id") instanceof byte[] verification)
            || verification.length != 16) return invalid();
        if (!Arrays.equals(value(row, "warehouse_id"), Ids.bytes(key.warehouseId()))
            || !Arrays.equals(value(row, "submission_id"), Ids.bytes(key.submissionId()))
            || !(row.get("work_round") instanceof Number round) || round.longValue() != key.workRound()
            || !Arrays.equals(value(row, "creator_device_id"), Ids.bytes(key.creatorDeviceId()))
            || !Arrays.equals(value(row, "temporary_authorization_id"), Ids.bytes(key.authorizationId())))
            return new SourceEvidenceIntakeReplay.Decision(SourceEvidenceIntakeReplay.Status.CONFLICT, null,
                SourceEvidenceIntakeReplay.TrustStatus.NOT_EVALUATED);
        return SourceEvidenceIntakeReplay.evaluate(request, new SourceEvidenceIntakeReplay.StoredReceipt(
            number(row, "receipt_version"), value(row, "canonical_request_bytes"), text(row, "request_sha256"),
            value(row, "original_response_bytes"), text(row, "original_response_sha256")));
    }

    private static SourceEvidenceIntakeReplay.Decision invalid() {
        return new SourceEvidenceIntakeReplay.Decision(SourceEvidenceIntakeReplay.Status.INTEGRITY_FAILURE, null,
            SourceEvidenceIntakeReplay.TrustStatus.NOT_EVALUATED);
    }
    private static String status(byte[] response) {
        var receipt = SubmissionJson.parse(response);
        String status = receipt.path("status").asText();
        if (!"VERIFY".equals(receipt.path("operationKind").asText())
            || !Set.of("PARTIAL", "VERIFIED_COMPLETE_AT_STOP").contains(status))
            throw new IllegalArgumentException("VERIFY_RECEIPT_STATUS");
        return status;
    }
    private static boolean matchingStatus(Map<String, Object> row) {
        try { return status(value(row, "original_response_bytes")).equals(row.get("receipt_status")); }
        catch (RuntimeException damaged) { return false; }
    }
    private static int number(Map<String, Object> row, String key) { return row.get(key) instanceof Number n ? n.intValue() : -1; }
    private static byte[] value(Map<String, Object> row, String key) { return row.get(key) instanceof byte[] b ? b : null; }
    private static String text(Map<String, Object> row, String key) { return row.get(key) instanceof String s ? s : null; }
}
