package com.acrylic.outbound.temporary.continuation;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.evidence.*;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static com.acrylic.outbound.temporary.continuation.TemporaryRoundFacts.conflict;

/** Technical stop consumes the already verified device proof; it grants no stock release. */
@Component
public class TemporaryRoundStopProof {
    private final JdbcTemplate jdbc;private final SourceEvidenceIntakeObjectFiles files;
    private final SourceEvidenceIntakeDeviceKeys keys;
    public TemporaryRoundStopProof(JdbcTemplate jdbc,SourceEvidenceIntakeObjectFiles files,SourceEvidenceIntakeDeviceKeys keys) {
        this.jdbc=jdbc;this.files=files;this.keys=keys;
    }
    public void require(SubmissionStore.Head head,JsonNode request) {
        var rows=jdbc.queryForList("""
            SELECT e.manifest_bytes,e.set_sha256,e.capture_high_watermark,e.audit_through_seq,
              o.file_key,o.bytes_sha256,o.byte_count
            FROM source_evidence_verification v JOIN source_evidence_set e ON e.id=v.set_id
              AND e.scope_id=v.scope_id AND e.submission_id=v.submission_id AND e.work_round=v.work_round
            JOIN submission s ON s.id=e.submission_id AND s.scope_id=e.scope_id AND s.work_round=e.work_round
            JOIN source_evidence_object o ON o.id=e.stop_object_id AND o.scope_id=e.scope_id
              AND o.submission_id=e.submission_id AND o.work_round=e.work_round AND o.kind='STOP_PROOF'
              AND o.warehouse_id=e.warehouse_id AND o.origin_device_id=s.freeze_device_id AND o.uploaded_by=s.operator_id
            WHERE v.id=? AND e.id=? AND s.id=? AND e.warehouse_id=? AND v.result='VERIFIED_COMPLETE_AT_STOP'
              AND v.set_sha256=e.set_sha256 AND v.set_sha256=? AND v.verification_version=1 AND v.algorithm_version=1
            """,Ids.bytes(uuid(request,"verificationId")),Ids.bytes(uuid(request,"sourceEvidenceSetId")),
            Ids.bytes(head.control().id()),Ids.bytes(head.warehouse()),text(request,"setSha256"));
        if(rows.size()!=1)throw conflict("TEMP_CONTINUATION_STOP_PROOF_REQUIRED");
        var row=rows.getFirst();JsonNode proof;
        try { proof=parse(files.read(new SourceEvidenceIntakeObjectFiles.Stored((String)row.get("file_key"),
            (String)row.get("bytes_sha256"),((Number)row.get("byte_count")).intValue()))); }
        catch(java.io.IOException invalid) { throw conflict("TEMP_CONTINUATION_STOP_PROOF_UNAVAILABLE"); }
        verify(head,proof,parse((byte[])row.get("manifest_bytes")),(String)row.get("set_sha256"),
            ((Number)row.get("capture_high_watermark")).longValue(),((Number)row.get("audit_through_seq")).longValue());
    }
    private void verify(SubmissionStore.Head head,JsonNode proof,JsonNode manifest,String hash,long capture,long audit) {
        if(!(manifest instanceof com.fasterxml.jackson.databind.node.ObjectNode m))throw conflict("TEMP_CONTINUATION_STOP_PROOF_INVALID");
        var c=head.control();var core=proof.path("core");var hashes=E2aProtocol.manifestHashes(m);
        if(!E2aProtocol.verifyStop(proof,keys.trusted(head.warehouse(),c.device()))
            || !hash.equals(hashes.setHash()) || !hash.equals(core.path("setHash").asText())
            || !hashes.manifestBytesHash().equals(core.path("manifestBytesHash").asText())
            || !c.id().toString().equals(core.path("submissionId").asText()) || core.path("workRound").asLong()!=c.round()
            || !c.temporary().authorization().toString().equals(core.path("sourceId").asText())
            || !head.actor().toString().equals(core.path("originalOperatorId").asText())
            || !head.warehouse().toString().equals(core.path("warehouseId").asText())
            || !c.device().toString().equals(core.path("freezeDeviceId").asText())
            || core.path("sourceRecoveryEpoch").asLong()!=Long.parseLong(c.epoch())
            || core.path("captureHighWatermark").asLong()!=capture || core.path("auditThroughSeq").asLong()!=audit)
            throw conflict("TEMP_CONTINUATION_STOP_PROOF_INVALID");
    }
}
