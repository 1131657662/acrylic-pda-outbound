package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionStore;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Repository
public class SourceEvidenceFacts {
    private final JdbcTemplate jdbc;
    public SourceEvidenceFacts(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    JsonNode physical(SubmissionStore.Head head) {
        byte[] id=Ids.bytes(head.control().id());
        String captures=jdbc.queryForObject("SELECT COUNT(*) FROM submitted_capture WHERE submission_id=?",String.class,id);
        String commitments=jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE submission_id=?",String.class,id);
        return object().put("knownSubmittedCaptureCount",captures).put("knownCommittedBaleCount",commitments)
            .put("completeness","UNKNOWN_OR_PARTIAL").put("complete",false).put("vehicleEmptyProven",false)
            .put("preAbortCommitmentSetProven",false);
    }
    JsonNode audit(SubmissionStore.Head head) {
        var c=head.control();var source=c.temporary();
        String count=jdbc.queryForObject("""
            SELECT COUNT(*) FROM pda_audit_event WHERE origin_device_id=? AND client_order_id=?
             AND temporary_authorization_id=? AND warehouse_id=?
            """,String.class,Ids.bytes(source.creator()),Ids.bytes(source.client()),Ids.bytes(source.authorization()),Ids.bytes(head.warehouse()));
        var prefixes=jdbc.queryForList("SELECT through_seq FROM pda_audit_checkpoint WHERE recovery_epoch=? AND origin_device_id=?",
            String.class,c.epoch(),Ids.bytes(source.creator()));
        return object().put("originDeviceId",source.creator().toString()).put("sourceRecoveryEpoch",c.epoch())
            .put("knownSourceEventCount",count).put("storedDeviceContinuousThroughSeq",prefixes.isEmpty()?null:prefixes.getFirst())
            .put("deviceEvidenceComplete",false).put("sourceEvidenceComplete",false)
            .put("scope","STORED_DEVICE_PREFIX_AT_SOURCE_EPOCH");
    }
}
