package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class RelayAssociationGroups {
    private final RelayAssociationGroupStore members;private final RelayAssociationStore store;
    private final RelayIndependentReconciliation reconciliation;private final RelayPhysicalChecks physical;
    private final RelayAssociationConflicts conflicts;private final RelayConflicts decisions;private final boolean enabled;
    public RelayAssociationGroups(RelayAssociationGroupStore members,RelayAssociationStore store,RelayIndependentReconciliation reconciliation,
        RelayPhysicalChecks physical,RelayAssociationConflicts conflicts,RelayConflicts decisions,
        @Value("${outbound.rescue.independent-multitask-enabled:false}") boolean enabled) {
        this.members=members;this.store=store;this.reconciliation=reconciliation;this.physical=physical;
        this.conflicts=conflicts;this.decisions=decisions;this.enabled=enabled;
    }
    public void require(JsonNode request) {
        if(request.has("additionalTasks") && !enabled)
            throw new BusinessException(409,"INDEPENDENT_MULTITASK_DISABLED","独立多任务关联尚未开放，原事实保留");
    }
    public List<JsonNode> prepare(RelayAssociationStore.Row row,JsonNode mainTarget) {
        var contexts=new ArrayList<JsonNode>();contexts.add(row.context());
        for(var task:row.request().path("additionalTasks")) {
            var reference=RelayAssociationGroupPolicy.request(row.request(),task);UUID relay=uuid(task,"relayId");
            var context=reconciliation.snapshot(row.scope().warehouse(),relay);
            var check=physical.read(row.scope().warehouse(),relay,uuid(task,"physicalCheckId"));
            var conflict=conflicts.resolve(row.scope().warehouse(),relay,reference);
            var target=RelayAssociationPolicy.member(reference,context,check.path("evidence"),row.scope().actor(),row.at(),conflict);
            if(!target.equals(mainTarget))throw RelayAssociationPolicy.changed();
            contexts.add(context);available(row,context,reference);
        }
        if(row.request().has("additionalTasks"))RelayAssociationGroupPolicy.distinct(contexts);
        RelayAssociationPlan.completeGroup(row.request(),mainTarget,contexts);
        var combined=object().putArray("members");contexts.forEach(combined::add);
        if(canonical(combined).getBytes(java.nio.charset.StandardCharsets.UTF_8).length>16*1024*1024-1)
            throw RelayAssociationPolicy.invalid("全组关联上下文超过资源上限");
        return contexts.subList(1,contexts.size());
    }
    private void available(RelayAssociationStore.Row row,JsonNode context,JsonNode request) {
        var original=context.path("original");var old=row.scope();
        store.available(new RelayAssociationStore.Scope(old.warehouse(),uuid(original,"relayId"),uuid(original,"deviceId"),
            uuid(original,"taskId"),old.target(),old.actor(),uuid(request,"physicalCheckId")));
    }
    public void insert(RelayAssociationStore.Row row,List<JsonNode> contexts) {
        for(var context:contexts) {
            var task=reference(row,text(context.path("original"),"relayId"));
            members.insert(row,context,RelayAssociationGroupPolicy.request(row.request(),task));
        }
    }
    public ArrayNode read(RelayAssociationStore.Row row) {
        var contexts=members.read(row);var result=object().putArray("members");
        if(contexts.size()!=row.request().path("additionalTasks").size())throw RelayAssociationStore.corrupt();
        for(var context:contexts) {
            String relay=text(context.path("original"),"relayId");var task=reference(row,relay);
            var check=physical.read(row.scope().warehouse(),UUID.fromString(relay),uuid(task,"physicalCheckId"));
            if(!check.path("originalContext").equals(context))throw RelayAssociationStore.corrupt();
            var member=object();member.set("evidence",task);member.set("originalContext",context);member.set("physicalEvidence",check);
            if(task.has("conflictDecisionId")) {
                var decision=decisions.read(row.scope().warehouse(),UUID.fromString(relay),uuid(task,"conflictDecisionId"));
                if(!decision.path("originalContext").path("selected").path("context").equals(context))throw RelayAssociationStore.corrupt();
                member.set("conflictEvidence",decision);
            }
            result.add(member);
        }
        return result;
    }
    private JsonNode reference(RelayAssociationStore.Row row,String relay) {
        for(var task:row.request().path("additionalTasks"))if(text(task,"relayId").equals(relay))return task;
        throw RelayAssociationStore.corrupt();
    }
}
