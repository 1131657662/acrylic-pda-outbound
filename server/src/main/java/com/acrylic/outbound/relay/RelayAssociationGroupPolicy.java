package com.acrylic.outbound.relay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Each original remains separate; only the supervisor's explicit selection is aggregated. */
final class RelayAssociationGroupPolicy {
    private RelayAssociationGroupPolicy() { }
    static void validate(JsonNode body) {
        if(!body.has("additionalTasks"))return;
        var tasks=body.path("additionalTasks");var seen=new HashSet<UUID>();
        if(!tasks.isArray() || tasks.isEmpty() || tasks.size()>19)throw RelayAssociationPolicy.invalid("须显式选择2至20个原任务");
        for(var task:tasks) {
            RelayPhysicalPolicy.exact(task,"relayId contextSha256 physicalCheckId candidateExclusions"+(task.has("conflictDecisionId")?" conflictDecisionId":""));
            if(!seen.add(RelayPhysicalPolicy.identifier(task,"relayId")))throw RelayAssociationPolicy.invalid("原件重复选择");
            RelayAssociationPolicy.validate(request(body,task));
        }
    }
    static ObjectNode request(JsonNode body,JsonNode task) {
        var request=object();request.set("contextSha256",task.path("contextSha256"));
        request.set("physicalCheckId",task.path("physicalCheckId"));request.set("candidateExclusions",task.path("candidateExclusions"));
        request.set("targetOrderId",body.path("targetOrderId"));request.set("reason",body.path("reason"));
        if(task.has("conflictDecisionId"))request.set("conflictDecisionId",task.path("conflictDecisionId"));return request;
    }
    static void distinct(List<JsonNode> contexts) {
        var tasks=new HashSet<String>();var barcodes=new HashSet<String>();var bales=new HashSet<String>();
        for(var context:contexts) {
            var original=context.path("original");
            if(!tasks.add(text(original,"deviceId")+"/"+text(original,"taskId")))throw RelayAssociationPolicy.blocked("同一原任务不得重复选择");
            for(var row:context.path("inventory")) {
                String barcode=text(row.path("original"),"barcode"),bale=text(row.path("server"),"baleId");
                if(barcode.isBlank() || bale.isBlank() || !barcodes.add(barcode) || !bales.add(bale))
                    throw RelayAssociationPolicy.blocked("合计有效包有重复或缺少可核验库存标识，不能重复计入计划");
            }
        }
    }
}
