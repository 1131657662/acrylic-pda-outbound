package com.acrylic.outbound.temporary.continuation;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeObjectFiles;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static com.acrylic.outbound.temporary.continuation.TemporaryRoundFacts.conflict;

/** Reads the actual frozen predecessor, including the independent pre-intake STOP archive. */
@Component
public class TemporaryRoundPredecessor {
    private final JdbcTemplate jdbc;private final SubmissionFiles submissions;private final SourceEvidenceIntakeObjectFiles objects;
    public TemporaryRoundPredecessor(JdbcTemplate jdbc,SubmissionFiles submissions,SourceEvidenceIntakeObjectFiles objects) {
        this.jdbc=jdbc;this.submissions=submissions;this.objects=objects;
    }
    public JsonNode read(TemporaryRoundControl request,TemporaryRoundFacts.Source source) {
        var accepted=jdbc.queryForList("SELECT payload_sha256 FROM submission_payload WHERE submission_id=?",
            String.class,Ids.bytes(source.previous()));
        String expected=request.predecessor().payloadSha256();
        byte[] raw;
        if(!accepted.isEmpty()) {
            if(accepted.size()!=1 || !accepted.getFirst().equals(expected))throw conflict("TEMP_CONTINUATION_PREDECESSOR_MISMATCH");
            try { raw=submissions.read(expected); }
            catch(java.io.UncheckedIOException failure) { throw conflict("TEMP_CONTINUATION_PREDECESSOR_UNAVAILABLE"); }
        } else raw=local(source,expected);
        var body=parse(raw);
        if(!sha(body).equals(expected) || !Arrays.equals(raw,canonical(body).getBytes(StandardCharsets.UTF_8)))
            throw conflict("TEMP_CONTINUATION_PREDECESSOR_MISMATCH");
        identity(source,body);return body;
    }
    private byte[] local(TemporaryRoundFacts.Source source,String expected) {
        var rows=jdbc.query("""
            SELECT o.file_key,o.bytes_sha256,o.byte_count
            FROM source_evidence_item i JOIN source_evidence_object o ON o.id=i.object_id
              AND o.scope_id=i.scope_id AND o.submission_id=i.submission_id AND o.work_round=i.work_round
            WHERE i.set_id=? AND i.scope_id=? AND i.submission_id=? AND i.work_round=?
              AND i.item_kind='STOP' AND i.record_status='FROZEN' AND i.original_record_id=i.submission_id
              AND o.kind='LOCAL_FROZEN_PAYLOAD' AND o.origin_device_id=? AND o.warehouse_id=?
              AND o.uploaded_by=? AND i.record_sha256=o.bytes_sha256 AND o.bytes_sha256=?
            """,(rs,n)->new SourceEvidenceIntakeObjectFiles.Stored(rs.getString(1),rs.getString(2),rs.getInt(3)),
            Ids.bytes(source.evidenceSet()),Ids.bytes(source.scope()),Ids.bytes(source.previous()),source.fromRound(),
            Ids.bytes(source.device()),Ids.bytes(source.warehouse()),Ids.bytes(source.operator()),expected);
        if(rows.size()!=1)throw conflict("TEMP_CONTINUATION_PREDECESSOR_UNAVAILABLE");
        try { return objects.read(rows.getFirst()); }
        catch(java.io.IOException failure) { throw conflict("TEMP_CONTINUATION_PREDECESSOR_UNAVAILABLE"); }
    }
    private void identity(TemporaryRoundFacts.Source source,JsonNode body) {
        // Higher-round STOP verification is not implemented; do not infer its provenance from counts.
        if(source.fromRound()!=1)throw conflict("TEMP_CONTINUATION_PREDECESSOR_ROUND_UNSUPPORTED");
        var c=SubmissionControl.read(uuid(body,"submissionId"),body);
        if(c.temporary()==null || !c.id().equals(source.previous()) || c.round()!=source.fromRound()
            || !c.device().equals(source.device()) || !c.epoch().equals(source.epoch())
            || !c.temporary().authorization().equals(source.previousAuthorization()))
            throw conflict("TEMP_CONTINUATION_PREDECESSOR_MISMATCH");
        int matches=jdbc.queryForObject("SELECT COUNT(*) FROM submission_scope WHERE id=? AND creator_device_id=? AND client_order_id=?",
            Integer.class,Ids.bytes(source.scope()),Ids.bytes(c.temporary().creator()),Ids.bytes(c.temporary().client()));
        if(matches!=1)throw conflict("TEMP_CONTINUATION_PREDECESSOR_MISMATCH");
    }
}
