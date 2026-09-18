package com.acrylic.outbound.pc;

import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Repository
public class PcExceptionStore {
    private final JdbcTemplate jdbc;
    public PcExceptionStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Case find(PcStore.Work work,UUID id) {
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(id),work_round,status,snapshot_text,snapshot_sha256,reason,stopped_at
            FROM pc_exception_case WHERE work_id=? AND (? IS NULL OR id=?) ORDER BY work_round DESC LIMIT 1
            """,(rs,n)->new Case(UUID.fromString(rs.getString(1)),rs.getLong(2),rs.getString(3),parse(rs.getString(4)),rs.getString(5),rs.getString(6),rs.getTimestamp(7).toInstant().toString()),
            Ids.bytes(work.id()),id==null?null:Ids.bytes(id),id==null?null:Ids.bytes(id));
        if(rows.isEmpty() && id!=null) throw new BusinessException(404,"PC_EXCEPTION_NOT_FOUND","本单没有此异常处置记录");
        return rows.isEmpty()?null:rows.getFirst();
    }
    public List<History> history(PcStore.Work work) {
        return jdbc.query("SELECT BIN_TO_UUID(id),work_round,status,reason,stopped_at FROM pc_exception_case WHERE work_id=? ORDER BY work_round DESC",
            (rs,n)->new History(rs.getString(1),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getTimestamp(5).toInstant().toString()),Ids.bytes(work.id()));
    }
    public List<Plan> plans(Case stopped) {
        return jdbc.query("""
            SELECT BIN_TO_UUID(p.id),p.status,p.payload_text,p.payload_sha256,BIN_TO_UUID(p.approved_by),p.handover_evidence,
              p.requested_at,p.applied_at FROM pc_exception_plan p WHERE p.case_id=? ORDER BY p.id DESC
            """,(rs,n)->new Plan(UUID.fromString(rs.getString(1)),rs.getString(2),parse(rs.getString(3)),rs.getString(4),rs.getString(5),rs.getString(6),
                rs.getTimestamp(7).toInstant().toString(),rs.getTimestamp(8)==null?null:rs.getTimestamp(8).toInstant().toString()),Ids.bytes(stopped.id()));
    }
    public Plan current(Case stopped) { var plans=plans(stopped);return plans.isEmpty()?null:plans.getFirst(); }
    public List<Review> reviews(Case stopped) {
        return jdbc.query("""
            SELECT BIN_TO_UUID(r.plan_id),r.decision,r.reason,u.display_name,r.created_at FROM pc_exception_review r
            INNER JOIN pc_exception_plan p ON p.id=r.plan_id INNER JOIN app_user u ON u.id=r.actor_id WHERE p.case_id=? ORDER BY r.id
            """,(rs,n)->new Review(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getTimestamp(5).toInstant().toString()),Ids.bytes(stopped.id()));
    }
    public JsonNode dispositions(PcStore.Work work) {
        var result=object().putArray("items");jdbc.query("""
            SELECT BIN_TO_UUID(d.id),BIN_TO_UUID(d.plan_id),BIN_TO_UUID(d.entry_id),d.action,d.expected_cycle,d.record_sha256,d.physical_evidence
            FROM pc_disposition d INNER JOIN pc_entry e ON e.id=d.entry_id WHERE e.work_id=? ORDER BY e.sequence_no
            """,rs->{result.add(object().put("id",rs.getString(1)).put("planId",rs.getString(2)).put("entryId",rs.getString(3))
                .put("action",rs.getString(4)).put("expectedCycle",rs.getLong(5)).put("recordSha256",rs.getString(6)).put("physicalEvidence",rs.getString(7)));},Ids.bytes(work.id()));
        return result;
    }
    public void saveCase(PcStore.Work work,Stopped input,UUID actor) {
        jdbc.update("""
            INSERT INTO pc_exception_case(id,work_id,work_round,previous_operator_id,status,snapshot_text,snapshot_sha256,reason,stopped_by,stopped_at)
            VALUES(?,?,?,?,'STOPPED',?,?,?,?,UTC_TIMESTAMP(3))
            """,Ids.bytes(input.id()),Ids.bytes(work.id()),work.round(),Ids.bytes(work.operator()),canonical(input.snapshot()),sha(input.snapshot()),input.reason().strip(),Ids.bytes(actor));
    }
    public record Stopped(UUID id,JsonNode snapshot,String reason) { }
    public record Case(UUID id,long round,String status,JsonNode snapshot,String snapshotSha256,String reason,String stoppedAt) { }
    public record Plan(UUID id,String status,JsonNode body,String payloadSha256,String approvedBy,String handoverEvidence,String requestedAt,String appliedAt) { }
    public record History(String id,long round,String status,String reason,String stoppedAt) { }
    public record Review(String planId,String decision,String reason,String actorName,String createdAt) { }
}
