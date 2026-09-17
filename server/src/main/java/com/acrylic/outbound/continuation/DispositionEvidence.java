package com.acrylic.outbound.continuation;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.recall.RecallStore;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static com.acrylic.outbound.continuation.ContinuationChecks.conflict;

@Repository
public class DispositionEvidence {
    private final JdbcTemplate jdbc;
    public DispositionEvidence(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void validate(RecallStore.Head recall,JsonNode body,JsonNode command) {
        var scans=new HashMap<String,JsonNode>();DispositionProofs.effective(body).forEach(s->scans.put(text(s,"barcode"),s));
        for(var row:array(command,"dispositions")) {
            var scan=scans.get(text(row,"barcode"));require(scan!=null,"实物处置不在当前恢复包有效集合中");
            if(text(row,"action").equals("RETAIN")) {
                int approved=jdbc.queryForObject("SELECT COUNT(*) FROM physical_disposition_evidence WHERE recovery_package_id=? AND origin_scan_id=?",Integer.class,
                    Ids.bytes(uuid(body,"recoveryPackageId")),Ids.bytes(uuid(scan,"captureId")));
                conflict(approved==0,"DISPOSITION_EVIDENCE_REQUIRED","已批准卸货或隔离的包不能继续作为保留包");continue;
            }
            var e=get(uuid(row,"approvedEvidenceId"));
            conflict(e.order().equals(recall.order()) && e.recovery().equals(uuid(body,"recoveryPackageId")) && e.epoch().equals(recall.epoch())
                && e.scan().equals(uuid(scan,"captureId")) && e.bale().equals(uuid(scan,"baleId")) && e.cycle()==number(scan,"expectedDispatchCycle")
                && e.action().equals(text(row,"action")) && e.recordHash().equals(sha(scan)) && e.applied()==null,
                "DISPOSITION_EVIDENCE_MISMATCH","减少行缺少与原包、周期、恢复包一致的主管批准凭据");
        }
    }
    public Head get(UUID id) {
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(id),BIN_TO_UUID(recovery_package_id),BIN_TO_UUID(order_id),BIN_TO_UUID(bale_id),BIN_TO_UUID(origin_scan_id),
            expected_cycle,action,LOWER(HEX(record_sha256)),recovery_epoch,BIN_TO_UUID(applied_submission_id),physical_evidence
            FROM physical_disposition_evidence WHERE id=?
            """,(rs,n)->new Head(UUID.fromString(rs.getString(1)),UUID.fromString(rs.getString(2)),UUID.fromString(rs.getString(3)),
                UUID.fromString(rs.getString(4)),UUID.fromString(rs.getString(5)),rs.getLong(6),rs.getString(7),rs.getString(8),rs.getString(9),rs.getString(10),rs.getString(11)),Ids.bytes(id));
        conflict(rows.size()==1,"DISPOSITION_EVIDENCE_MISMATCH","实物处置凭据不存在或不匹配");return rows.getFirst();
    }
    public JsonNode list(UUID id,boolean batch) {
        var ids=jdbc.queryForList(batch?"SELECT BIN_TO_UUID(id) FROM physical_disposition_evidence WHERE approval_batch_id=? ORDER BY id":
            "SELECT BIN_TO_UUID(id) FROM physical_disposition_evidence WHERE recovery_package_id=? ORDER BY id",String.class,Ids.bytes(id));
        var result=object().put("stockReleased",false);var rows=result.putArray("evidence");
        for(var key:ids) {
            var e=get(UUID.fromString(key));var row=object().put("approvedEvidenceId",e.id().toString()).put("captureId",e.scan().toString())
                .put("baleId",e.bale().toString()).put("expectedDispatchCycle",Long.toString(e.cycle())).put("action",e.action())
                .put("physicalEvidence",e.physical()).put("recoveryPackageId",e.recovery().toString());rows.add(row);
        }
        return result;
    }
    public record Head(UUID id,UUID recovery,UUID order,UUID bale,UUID scan,long cycle,String action,String recordHash,String epoch,String applied,String physical) {}
}
