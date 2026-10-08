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
        var c=source.fromRound()==1?SubmissionControl.read(uuid(body,"submissionId"),body)
            :body.has("temporaryHandover")?com.acrylic.outbound.handover.TemporaryHandoverControl.read(body).control()
            :TemporaryRoundControl.read(body).control();
        if(c.temporary()==null || !c.id().equals(source.previous()) || c.round()!=source.fromRound()
            || !c.device().equals(source.device()) || !c.epoch().equals(source.epoch())
            || !c.temporary().authorization().equals(source.previousAuthorization()))
            throw conflict("TEMP_CONTINUATION_PREDECESSOR_MISMATCH");
        int matches=jdbc.queryForObject("""
            SELECT COUNT(*) FROM submission_scope c
            JOIN submission s ON s.scope_id=c.id AND s.id=? AND s.work_round=? AND s.temporary_authorization_id=?
            LEFT JOIN temporary_source_continuation_submission r ON r.submission_id=s.id AND r.scope_id=c.id
              AND r.to_work_round=s.work_round AND r.from_work_round=s.work_round-1
            LEFT JOIN temporary_source_continuation_activation a ON a.ticket_id=r.ticket_id AND a.scope_id=c.id
              AND a.from_work_round=r.from_work_round AND a.new_authorization_id=s.temporary_authorization_id
              AND a.target_device_id=s.freeze_device_id AND a.target_operator_id=s.operator_id
            LEFT JOIN temporary_handover_submission hr ON hr.submission_id=s.id AND hr.scope_id=c.id
              AND hr.to_work_round=s.work_round AND hr.from_work_round=s.work_round-1
            LEFT JOIN temporary_handover_activation ha ON ha.ticket_id=hr.ticket_id
              AND ha.to_work_round=hr.to_work_round AND ha.new_authorization_id=s.temporary_authorization_id
              AND ha.target_device_id=s.freeze_device_id AND ha.target_user_id=s.operator_id
            JOIN temporary_work_origin o ON o.creator_device_id=s.freeze_device_id
              AND o.client_order_id=COALESCE(ha.new_client_order_id,a.new_client_order_id,c.client_order_id)
              AND o.authorization_id=s.temporary_authorization_id
            WHERE c.id=? AND o.creator_device_id=? AND o.client_order_id=?
              AND ((s.work_round=1 AND c.creator_device_id=s.freeze_device_id AND a.ticket_id IS NULL AND ha.ticket_id IS NULL)
                OR (s.work_round>1 AND ((a.ticket_id IS NOT NULL AND ha.ticket_id IS NULL)
                  OR (a.ticket_id IS NULL AND ha.ticket_id IS NOT NULL))))
            """,Integer.class,Ids.bytes(c.id()),c.round(),Ids.bytes(c.temporary().authorization()),
            Ids.bytes(source.scope()),Ids.bytes(c.temporary().creator()),Ids.bytes(c.temporary().client()));
        if(matches!=1)throw conflict("TEMP_CONTINUATION_PREDECESSOR_MISMATCH");
    }
}
