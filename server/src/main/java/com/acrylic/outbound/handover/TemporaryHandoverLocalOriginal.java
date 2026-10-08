package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeObjectFiles;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Reads only the canonical LOCAL original already bound into this round's verified E2A set. */
@Component
final class TemporaryHandoverLocalOriginal {
    private final JdbcTemplate jdbc;private final SourceEvidenceIntakeObjectFiles files;
    TemporaryHandoverLocalOriginal(JdbcTemplate jdbc,SourceEvidenceIntakeObjectFiles files) {this.jdbc=jdbc;this.files=files;}

    JsonNode read(SubmissionStore.Head head,JsonNode request) {
        var rows=jdbc.query("""
            SELECT o.file_key,o.bytes_sha256,o.byte_count FROM source_evidence_set e
            JOIN source_evidence_item i ON i.set_id=e.id AND i.scope_id=e.scope_id AND i.submission_id=e.submission_id
              AND i.work_round=e.work_round AND i.warehouse_id=e.warehouse_id
            JOIN source_evidence_object o ON o.id=i.object_id AND o.scope_id=i.scope_id AND o.submission_id=i.submission_id
              AND o.work_round=i.work_round AND o.warehouse_id=i.warehouse_id
            WHERE e.id=? AND e.submission_id=? AND e.work_round=? AND e.warehouse_id=? AND e.set_sha256=?
              AND i.item_kind='STOP' AND i.record_status='FROZEN' AND i.original_record_id=e.submission_id
              AND i.original_device_seq=e.audit_through_seq AND o.kind='LOCAL_FROZEN_PAYLOAD'
              AND i.record_sha256=o.bytes_sha256
              AND o.origin_device_id=? AND o.uploaded_by=?
            """,(rs,n)->new SourceEvidenceIntakeObjectFiles.Stored(rs.getString(1),rs.getString(2),rs.getInt(3)),
            Ids.bytes(uuid(request,"sourceEvidenceSetId")),Ids.bytes(head.control().id()),head.control().round(),
            Ids.bytes(head.warehouse()),text(request,"setSha256"),Ids.bytes(head.control().device()),Ids.bytes(head.actor()));
        if(rows.size()!=1)throw TemporaryHandoverFrozenSource.changed();
        try {
            byte[] raw=files.read(rows.getFirst());var body=parse(raw);
            if(!sha(body).equals(rows.getFirst().sha256()) || !Arrays.equals(raw,canonical(body).getBytes(StandardCharsets.UTF_8)))
                throw TemporaryHandoverFrozenSource.changed();
            return body;
        } catch(java.io.IOException invalid) {throw TemporaryHandoverFrozenSource.changed();}
    }
}
