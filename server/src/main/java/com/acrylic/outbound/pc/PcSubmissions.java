package com.acrylic.outbound.pc;

import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Repository
public class PcSubmissions {
    private final JdbcTemplate jdbc;
    public PcSubmissions(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Submission current(PcStore.Work work) {
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(id),work_round,status,payload_sha256,actual_count,weight_g,short_reason,issues_json,receipt_json,created_at
            FROM pc_submission WHERE work_id=? ORDER BY work_round DESC LIMIT 1
            """,(rs,n)->new Submission(rs.getString(1),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),
                parse(rs.getString(8)),rs.getString(9)==null?null:parse(rs.getString(9)),rs.getTimestamp(10).toInstant().toString()),Ids.bytes(work.id()));
        return rows.isEmpty()?null:rows.getFirst();
    }
    public String payload(UUID id) { return jdbc.queryForObject("SELECT payload_text FROM pc_submission WHERE id=?",String.class,Ids.bytes(id)); }
    public void save(PcStore.Work work,Input input,UUID actor) {
        jdbc.update("""
            INSERT INTO pc_submission(id,work_id,work_round,status,payload_sha256,payload_text,actual_count,weight_g,short_reason,issues_json,created_by,created_at)
            VALUES(?,?,?,'REVIEW_REQUIRED',?,?,?,?,?,?,?,UTC_TIMESTAMP(3))
            """,Ids.bytes(input.id()),Ids.bytes(work.id()),work.round(),input.hash(),input.payload(),input.count(),input.weight(),input.reason(),canonical(input.issues()),Ids.bytes(actor));
    }
    public record Input(UUID id,String hash,String payload,long count,long weight,String reason,JsonNode issues) { }
    public record Submission(String id,long round,String status,String payloadSha256,String actualCount,String weightGrams,String shortReason,
        JsonNode issues,JsonNode receipt,String createdAt) { }
}
