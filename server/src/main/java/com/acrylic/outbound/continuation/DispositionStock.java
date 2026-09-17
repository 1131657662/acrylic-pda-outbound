package com.acrylic.outbound.continuation;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static com.acrylic.outbound.continuation.ContinuationChecks.conflict;

@Component
public class DispositionStock {
    private final DispositionEvidence evidence;private final JdbcTemplate jdbc;
    public DispositionStock(DispositionEvidence evidence,JdbcTemplate jdbc) { this.evidence=evidence;this.jdbc=jdbc; }
    public ArrayNode held(JsonNode body,JsonNode proofs) {
        var rows=DispositionProofs.effective(body,proofs);var pending=new HashSet<String>();
        for(var p:proofs)if(evidence.get(uuid(p,"approvedEvidenceId")).applied()==null)pending.add(text(p,"captureId"));
        for(var row:array(body,"scans"))if(pending.contains(text(row,"captureId")))rows.add(row);
        return rows;
    }
    public void apply(SubmissionStore.Head head,JsonNode proofs) {
        var rows=new ArrayList<JsonNode>();proofs.forEach(rows::add);rows.sort(Comparator.comparing(p->text(p,"baleId")));
        for(var row:rows) {
            var e=evidence.get(uuid(row,"approvedEvidenceId"));if(e.applied()!=null)continue;
            byte[] bale=Ids.bytes(e.bale());long cycle=jdbc.queryForObject("SELECT dispatch_cycle FROM bale WHERE id=? FOR UPDATE",Long.class,bale);
            conflict(cycle==e.cycle(),"DISPATCH_CYCLE_CONFLICT","实物处置原周期变化，保留占用并先对账");
            var c=head.control();int changed;
            if(e.action().equals("RELEASE"))changed=jdbc.update("""
                DELETE FROM bale_commitment WHERE bale_id=? AND order_id=? AND continuation_id=? AND state='CONTINUATION_RESERVED' AND dispatch_cycle=?
                """,bale,Ids.bytes(c.order()),Ids.bytes(c.continuation()),cycle);
            else changed=jdbc.update("""
                UPDATE bale_commitment SET state='QUARANTINED',continuation_id=NULL,disposition_evidence_id=?
                WHERE bale_id=? AND order_id=? AND continuation_id=? AND state='CONTINUATION_RESERVED' AND dispatch_cycle=?
                """,Ids.bytes(e.id()),bale,Ids.bytes(c.order()),Ids.bytes(c.continuation()),cycle);
            conflict(changed==1,"DISPOSITION_STOCK_CONFLICT","处置包占用已变化，停止接收新批次");
            jdbc.update("UPDATE physical_disposition_evidence SET applied_submission_id=? WHERE id=?",Ids.bytes(c.id()),Ids.bytes(e.id()));
        }
    }
}
