package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.SubmissionFiles;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeObjectFiles;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Reads scan hashes from the stopped original; handover owner hashes describe a different record. */
@Repository
public class TemporaryContinuationRetainedScans {
    private final JdbcTemplate jdbc;
    private final SubmissionFiles submissions;
    private final SourceEvidenceIntakeObjectFiles objects;
    public TemporaryContinuationRetainedScans(JdbcTemplate jdbc, SubmissionFiles submissions,
            SourceEvidenceIntakeObjectFiles objects) {
        this.jdbc=jdbc;this.submissions=submissions;this.objects=objects;
    }
    Map<UUID, Scan> read(PhysicalDispositionVerification.Verified proof) {
        var hashes=jdbc.queryForList("SELECT payload_sha256 FROM submission_payload WHERE submission_id=?",
            String.class,Ids.bytes(proof.submission()));
        if(hashes.size()>1)throw conflict();
        final byte[] raw;
        try { raw=hashes.isEmpty()?local(proof):submissions.read(hashes.getFirst()); }
        catch(java.io.IOException | java.io.UncheckedIOException unavailable) { throw conflict(); }
        var body=parse(raw);
        if(!Arrays.equals(raw,canonical(body).getBytes(StandardCharsets.UTF_8))
                || !uuid(body,"submissionId").equals(proof.submission()) || number(body,"workRound")!=proof.round())throw conflict();
        identity(proof,body);
        var result=new HashMap<UUID,Scan>();
        for(var scan:array(body,"scans")) {
            if(!"ACTIVE".equals(text(scan,"status")))continue;
            if(result.put(uuid(scan,"baleId"),new Scan(number(scan,"expectedDispatchCycle"),sha(scan)))!=null)throw conflict();
        }
        return Map.copyOf(result);
    }
    private byte[] local(PhysicalDispositionVerification.Verified proof) throws java.io.IOException {
        var files=jdbc.query("""
            SELECT o.file_key,o.bytes_sha256,o.byte_count
            FROM source_evidence_item i JOIN source_evidence_object o ON o.id=i.object_id
              AND o.scope_id=i.scope_id AND o.submission_id=i.submission_id AND o.work_round=i.work_round AND o.warehouse_id=i.warehouse_id
            JOIN submission s ON s.id=i.submission_id AND s.scope_id=i.scope_id AND s.work_round=i.work_round
              AND o.origin_device_id=s.freeze_device_id AND o.uploaded_by=s.operator_id
            WHERE i.set_id=? AND i.scope_id=? AND i.submission_id=? AND i.work_round=? AND i.warehouse_id=?
              AND i.item_kind='STOP' AND i.record_status='FROZEN' AND i.original_record_id=s.id
              AND o.kind='LOCAL_FROZEN_PAYLOAD' AND i.record_sha256=o.bytes_sha256
            """,(rs,n)->new SourceEvidenceIntakeObjectFiles.Stored(rs.getString(1),rs.getString(2),rs.getInt(3)),
            Ids.bytes(proof.set()),Ids.bytes(proof.scope()),Ids.bytes(proof.submission()),proof.round(),Ids.bytes(proof.warehouse()));
        if(files.size()!=1)throw conflict();
        return objects.read(files.getFirst());
    }
    private void identity(PhysicalDispositionVerification.Verified proof,JsonNode body) {
        Integer count=jdbc.queryForObject("""
            SELECT COUNT(*) FROM submission s JOIN submission_scope c ON c.id=s.scope_id
            WHERE s.id=? AND s.scope_id=? AND s.work_round=? AND c.warehouse_id=? AND s.status='ABORTED'
              AND s.freeze_device_id=? AND s.temporary_authorization_id=? AND s.source_recovery_epoch=?
            """,Integer.class,Ids.bytes(proof.submission()),Ids.bytes(proof.scope()),proof.round(),Ids.bytes(proof.warehouse()),
            Ids.bytes(uuid(body,"freezeDeviceId")),Ids.bytes(uuid(body,"authorizationId")),text(body,"sourceRecoveryEpoch"));
        if(!Integer.valueOf(1).equals(count))throw conflict();
    }
    private static BusinessException conflict() {
        return new BusinessException(409,"CONTINUATION_RETAINED_SOURCE_INVALID","Stopped original scan records cannot be verified");
    }
    record Scan(long cycle,String sha256) { }
}
