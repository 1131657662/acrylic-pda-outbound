package com.acrylic.outbound.continuation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Immutable scan facts are projected through a separately authenticated disposition list. */
public final class DispositionProofs {
    private DispositionProofs() {}
    public static ArrayNode rows(JsonNode body) {
        if(!body.has("dispositions"))return object().putArray("rows");
        require(body.path("dispositions").isArray(),"实物处置清单格式错误");return (ArrayNode)body.get("dispositions");
    }
    public static Set<String> excluded(JsonNode body) {
        var ids=new HashSet<String>();
        for(var row:rows(body))require(ids.add(text(row,"captureId")),"实物处置重复引用原输入");
        return ids;
    }
    public static ArrayNode effective(JsonNode body) { return effective(body,rows(body)); }
    public static ArrayNode effective(JsonNode body,JsonNode proofs) {
        var excluded=new HashSet<String>();proofs.forEach(p->excluded.add(text(p,"captureId")));
        var result=object().putArray("rows");
        for(var s:array(body,"scans"))if(text(s,"status").equals("ACTIVE") && !excluded.contains(text(s,"captureId")))result.add(s);
        return result;
    }
    public static ArrayNode compose(JsonNode body,JsonNode command) {
        var actions=new HashMap<String,JsonNode>();
        for(var row:array(command,"dispositions"))require(actions.put(text(row,"barcode"),row)==null,"处置清单条码重复");
        var active=effective(body);ContinuationChecks.conflict(actions.size()==active.size(),"RETAINED_SET_MISMATCH","须覆盖全部有效装载明细");
        var result=rows(body).deepCopy();
        for(var scan:active) {
            var row=actions.get(text(scan,"barcode"));require(row!=null,"处置缺少原条码");
            equal(number(row,"expectedDispatchCycle"),number(scan,"expectedDispatchCycle"),"处置周期不匹配");String action=text(row,"action");
            require(Set.of("RETAIN","RELEASE","QUARANTINE").contains(action),"实物处置动作无效");
            if(action.equals("RETAIN")) { require(!row.hasNonNull("approvedEvidenceId"),"保留行不可引用减少凭据");continue; }
            result.add(object().put("captureId",text(scan,"captureId")).put("baleId",text(scan,"baleId")).put("barcode",text(scan,"barcode"))
                .put("expectedDispatchCycle",text(scan,"expectedDispatchCycle")).put("action",action).put("approvedEvidenceId",uuid(row,"approvedEvidenceId").toString()));
        }
        return result;
    }
}
