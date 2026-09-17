package com.acrylic.outbound.continuation;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.recall.*;
import com.acrylic.outbound.pda.*;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Service
public class ContinuationActivation {
    private final RecallAccess access;private final RecallStore recalls;private final ContinuationStore store;private final PreparationAccess preparation;
    private final AssignmentStore assignments;private final OfflineSigner signer;private final JdbcTemplate jdbc;private final ObjectMapper json;
    public ContinuationActivation(RecallAccess access,RecallStore recalls,ContinuationStore store,PreparationAccess preparation,AssignmentStore assignments,
        OfflineSigner signer,JdbcTemplate jdbc,ObjectMapper json) {
        this.access=access;this.recalls=recalls;this.store=store;this.preparation=preparation;this.assignments=assignments;this.signer=signer;this.jdbc=jdbc;this.json=json;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode activate(UUID id,JsonNode command,String epoch) {
        var before=store.get(id);var r=recalls.get(before.recall());access.pda(r.assignment(),epoch,true);recalls.lock(r);var h=store.get(id);
        ContinuationChecks.conflict(Set.of("APPROVED","ACTIVATED").contains(h.status()),"CONTINUATION_NOT_APPROVED","续作须主管批准");
        var p=recalls.recovery(r.id());equal(text(command,"payloadSha256"),p.hash(),"须核对原恢复包摘要");
        equal(text(command,"retainedManifestSha256"),h.manifest(),"保留清单摘要不匹配");equal(number(command,"nextOrderScanSeq"),h.nextSeq(),"原输入高水位不匹配");
        var a=preparation.lock(UUID.fromString(h.assignment()),epoch);equal(text(command,"planSha256"),a.planSha256(),"原计划摘要不匹配");
        if(h.activation()!=null)return parse(h.activation().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        jdbc.update("UPDATE order_assignment SET download_ack_at=COALESCE(download_ack_at,UTC_TIMESTAMP(3)) WHERE id=?",Ids.bytes(UUID.fromString(a.id())));
        var value=object().put("continuationId",h.id().toString()).put("orderId",h.order().toString()).put("previousAssignmentId",r.assignment().toString())
            .put("previousSubmissionId",h.previous().toString()).put("status","ACTIVATED").put("workRound",Long.toString(h.toRound()))
            .put("recoveryPackageId",p.id().toString()).put("payloadSha256",p.hash()).put("retainedManifestSha256",h.manifest())
            .put("nextOrderScanSeq",Long.toString(h.nextSeq())).put("issuedRecoveryEpoch",epoch).put("stockReleased",false);
        value.set("assignment",json.valueToTree(assignments.get(UUID.fromString(a.id()))));
        var dispositions=store.dispositions(h);if(!dispositions.isEmpty())value.set("dispositions",dispositions);
        var claims=object().put("iss","acrylic-outbound").put("aud","acrylic-pda-continuation");claims.set("receipt",value.deepCopy());value.put("signedReceipt",signer.sign(claims).token());
        jdbc.update("UPDATE order_continuation SET status='ACTIVATED',activation_text=?,activated_at=UTC_TIMESTAMP(3) WHERE id=?",canonical(value),Ids.bytes(id));return value;
    }
}
