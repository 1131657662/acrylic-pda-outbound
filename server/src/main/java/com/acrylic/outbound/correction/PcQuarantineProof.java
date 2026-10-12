package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.JsonText;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Original snapshot, complete plan, independent review and application must agree on this exact entry. */
@Component
final class PcQuarantineProof {
    private final JdbcTemplate jdbc;
    private final JsonText json;
    private final PcQuarantineReceipts receipts;
    PcQuarantineProof(JdbcTemplate jdbc,JsonText json,PcQuarantineReceipts receipts) { this.jdbc=jdbc; this.json=json; this.receipts=receipts; }

    ObjectNode read(JsonNode origin) {
        var rows=jdbc.queryForList("""
            SELECT c.snapshot_text,c.snapshot_sha256,c.status case_status,p.payload_text,p.payload_sha256,p.status plan_status,
              BIN_TO_UUID(p.approved_by) approved_by,BIN_TO_UUID(p.applied_by) applied_by,BIN_TO_UUID(p.requested_by) requested_by,
              BIN_TO_UUID(c.stopped_by) stopped_by,p.approved_by IS NOT NULL approved,p.applied_at,p.handover_evidence,
              BIN_TO_UUID(p.target_user_id) target_user_id,c.work_round
            FROM pc_exception_plan p JOIN pc_exception_case c ON c.id=p.case_id WHERE p.id=UUID_TO_BIN(?)
            """,origin.path("planId").asText());
        if(rows.size()!=1) throw CorrectionCommand.corrupt();
        var row=rows.getFirst(); var snapshot=SubmissionJson.parse(row.get("snapshot_text").toString());
        var plan=SubmissionJson.parse(row.get("payload_text").toString());
        if(!SubmissionJson.sha(snapshot).equals(row.get("snapshot_sha256")) || !SubmissionJson.sha(plan).equals(row.get("payload_sha256"))
            || !"APPLIED".equals(row.get("case_status")) || !"APPLIED".equals(row.get("plan_status"))
            || row.get("applied_at")==null || row.get("handover_evidence")==null || row.get("approved_by")==null
            || !row.get("target_user_id").equals(row.get("applied_by"))) throw CorrectionCommand.corrupt();
        matches(snapshot,"workId",origin,"workId"); matches(snapshot,"orderId",origin,"orderId");
        matches(plan,"caseId",origin,"caseId");
        if(!plan.path("snapshotSha256").asText().equals(row.get("snapshot_sha256"))
            || !plan.path("targetUserId").asText().equals(row.get("applied_by"))) throw CorrectionCommand.corrupt();
        entry(snapshot,plan,origin);
        var result=SubmissionJson.object().put("proofType","PC_DATABASE_ORIGINALS"); result.set("snapshot",snapshot); result.set("plan",plan);
        result.set("review",review(origin,row.get("approved_by").toString()));
        result.set("commands",receipts.read(origin,row));
        result.put("receiptId",result.at("/commands/apply/receiptId").asText());
        result.putObject("actor").put("principalId",row.get("approved_by").toString());
        result.put("recordedAt",result.at("/review/approvedAt").asText());
        result.put("handoverEvidence",row.get("handover_evidence").toString()); return result;
    }

    private void entry(JsonNode snapshot,JsonNode plan,JsonNode origin) {
        var entries=java.util.stream.StreamSupport.stream(snapshot.path("entries").spliterator(),false)
            .filter(e->e.path("id").equals(origin.path("entryId"))).toList();
        var lines=java.util.stream.StreamSupport.stream(plan.path("lines").spliterator(),false)
            .filter(e->e.path("entryId").equals(origin.path("entryId"))).toList();
        if(entries.size()!=1 || lines.size()!=1) throw CorrectionCommand.corrupt();
        var entry=entries.getFirst(); var line=lines.getFirst();
        var current=currentEntry(origin);
        if(!json.digest(current).equals(origin.path("recordSha256").asText()) || !entry.equals(SubmissionJson.parse(json.encode(current))))
            throw CorrectionCommand.corrupt();
        if(!"ACTIVE".equals(entry.path("status").asText())
            || !entry.path("baleId").equals(origin.path("baleId")) || entry.path("cycle").asLong()!=origin.path("cycle").asLong()
            || line.path("expectedCycle").asLong()!=origin.path("cycle").asLong() || !"QUARANTINE".equals(line.path("action").asText())
            || !line.path("physicalEvidence").equals(origin.path("closeReason")) || !"ACTIVE".equals(origin.path("entryStatus").asText())
            || !origin.path("cycle").equals(origin.path("entryCycle"))
            || !origin.path("entryDispositionId").equals(origin.path("dispositionId"))) throw CorrectionCommand.corrupt();
    }
    private com.acrylic.outbound.pc.PcEntries.Entry currentEntry(JsonNode origin) {
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(e.id),BIN_TO_UUID(e.bale_id),b.barcode,e.sequence_no,e.dispatch_cycle,e.status,e.delivery_code,
              e.weight_g,e.raw_weight,e.model_snapshot,e.name_snapshot,e.operator_name,e.created_at,e.void_reason
            FROM pc_entry e JOIN bale b ON b.id=e.bale_id WHERE e.id=UUID_TO_BIN(?) AND e.work_id=UUID_TO_BIN(?)
            """,(rs,n)->new com.acrylic.outbound.pc.PcEntries.Entry(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),
                rs.getLong(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9),rs.getString(10),rs.getString(11),
                rs.getString(12),rs.getTimestamp(13).toInstant().toString(),rs.getString(14)),
            origin.path("entryId").asText(),origin.path("workId").asText());
        if(rows.size()!=1) throw CorrectionCommand.corrupt(); return rows.getFirst();
    }

    private JsonNode review(JsonNode origin,String approver) {
        var rows=jdbc.queryForList("""
            SELECT JSON_OBJECT('reviewId',BIN_TO_UUID(id),'principalId',BIN_TO_UUID(actor_id),'decision',decision,
              'reason',reason,'permissionVersion',CAST(permission_version AS CHAR),
              'approvedAt',DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%s.%fZ')) data
            FROM pc_exception_review WHERE plan_id=UUID_TO_BIN(?) ORDER BY id
            """,origin.path("planId").asText());
        if(rows.size()!=1) throw CorrectionCommand.corrupt();
        var review=SubmissionJson.parse(rows.getFirst().get("data").toString());
        if(!"APPROVE".equals(review.path("decision").asText()) || !approver.equals(review.path("principalId").asText()))
            throw CorrectionCommand.corrupt();
        return review;
    }
    private void matches(JsonNode first,String field,JsonNode second,String target) {
        if(!first.path(field).equals(second.path(target))) throw CorrectionCommand.corrupt();
    }
}
