package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Requires the retained original ABORTED head; an unsubmitted frozen body never becomes a server payload. */
final class RecoveryTemporaryEvidenceHead {
    private final JdbcTemplate jdbc;
    RecoveryTemporaryEvidenceHead(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    void requireMatches(RecoveryPinnedSource source) {
        check(source," FOR UPDATE");
    }
    void requireOriginal(RecoveryPinnedSource source) {
        check(source,"");
    }
    private void check(RecoveryPinnedSource source,String lock) {
        var id=source.identity();
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(s.scope_id),BIN_TO_UUID(c.warehouse_id),BIN_TO_UUID(s.freeze_device_id),BIN_TO_UUID(s.operator_id),
             BIN_TO_UUID(s.temporary_authorization_id),BIN_TO_UUID(c.creator_device_id),BIN_TO_UUID(c.client_order_id),
             BIN_TO_UUID(c.canonical_order_id),s.work_round,s.source_recovery_epoch,s.status,s.receipt_text,
             BIN_TO_UUID(c.current_submission_id),c.work_round,s.assignment_id,s.continuation_id,s.superseded_by,c.source_order_id,
             p.submission_id
            FROM submission s JOIN submission_scope c ON c.id=s.scope_id
             LEFT JOIN submission_payload p ON p.submission_id=s.id WHERE s.id=?
            """+lock,(rs,n) -> {
                String[] expected={id.scopeId().toString(),id.warehouseId().toString(),id.deviceId().toString(),id.operatorId().toString(),
                    id.qualificationId().toString(),id.creatorDeviceId().toString(),id.clientOrderId().toString(),
                    id.canonicalOrderId()==null?null:id.canonicalOrderId().toString()};
                for(int index=0;index<expected.length;index++) require(Objects.equals(expected[index],rs.getString(index+1)),"RECOVERY_EVIDENCE_HEAD_SOURCE");
                require(rs.getLong(9)==1 && rs.getString(10).equals(id.sourceRecoveryEpoch()) && rs.getString(11).equals("ABORTED")
                    && Arrays.equals(rs.getString(12).getBytes(StandardCharsets.UTF_8),source.original("result"))
                    && rs.getString(13).equals(id.submissionId().toString()) && rs.getLong(14)==1,"RECOVERY_EVIDENCE_HEAD_TERMINAL");
                for(int index=15;index<=19;index++) require(rs.getObject(index)==null,"RECOVERY_EVIDENCE_HEAD_CHAIN"); return true;
            },Ids.bytes(id.submissionId()));
        require(rows.size()==1,"RECOVERY_EVIDENCE_HEAD_MISSING");
        var receipt=SubmissionJson.parse(source.original("result"));
        require(receipt.path("payloadSha256").isNull() && receipt.path("payloadPresent").isBoolean()
            && !receipt.path("payloadPresent").booleanValue(),"RECOVERY_EVIDENCE_HEAD_PAYLOAD");
    }
}
