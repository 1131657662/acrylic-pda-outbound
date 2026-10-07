package com.acrylic.outbound.temporary.continuation;

import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.*;
import com.acrylic.outbound.temporary.command.TemporaryRetainedManifest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static com.acrylic.outbound.temporary.continuation.TemporaryRoundFacts.conflict;

/** Pure cumulative evidence validation. Input must come from the locked registration/source and original archives. */
@Component
public class TemporaryRoundEvidence {
    private final TemporarySubmissionEvidence evidence;
    public TemporaryRoundEvidence(TemporarySubmissionEvidence evidence) { this.evidence=evidence; }
    public void validate(Input input,JsonNode body) {
        require(input.previous().control().round()==1,"高轮次必须重验完整前驱链");
        validate(input,body,TemporaryEvidenceOrigins.initial(input.previous(),input.originalPlan()));
    }
    public TemporaryEvidenceOrigins validate(Input input,JsonNode body,TemporaryEvidenceOrigins previousOrigins) {
        var request=TemporaryRoundControl.read(body);var current=input.current();var previous=input.previous();
        equal(request.control(),current.control(),"续作控制头与正文不符");
        input.source().require(request,new SubmissionAccess.Actor(current.actor(),current.warehouse(),""));
        requireOriginal(input,request);
        evidence.validate(previous,input.originalPlan(),input.original(),previousOrigins);
        var origins=previousOrigins.append(current,input.plan(),input.original());
        TemporaryRoundPrefixes.require(input.original(),body,input.retained());
        fixedPlan(input,body);
        long expected=Math.addExact(number(input.original(),"captureHighWatermark"),1);
        if(input.source().nextSequence()!=expected)throw conflict("CONTINUATION_HIGH_WATERMARK_CHANGED");
        evidence.validate(current,input.plan(),body,origins);
        return origins;
    }
    private void requireOriginal(Input input,TemporaryRoundControl request) {
        var old=input.previous();var source=input.source();var c=old.control();
        equal(sha(input.original()),request.predecessor().payloadSha256(),"续作前驱原文摘要不符");
        var originalControl=c.round()==1?SubmissionControl.read(uuid(input.original(),"submissionId"),input.original())
            :TemporaryRoundControl.read(input.original()).control();
        equal(originalControl,c,"续作前驱控制头不符");
        require(c.temporary()!=null && c.round()==source.fromRound()
            && c.id().equals(source.previous()) && c.device().equals(source.device())
            && old.actor().equals(source.operator()) && old.warehouse().equals(source.warehouse())
            && "ABORTED".equals(old.status()) && c.epoch().equals(source.epoch()),"续作前驱来源不符");
        equal(input.originalPlan().authorization().id(),c.temporary().authorization(),"前驱原资格不符");
        equal(input.plan().authorization().id(),source.authorization(),"新轮独立资格不符");
        equal(c.temporary().authorization(),source.previousAuthorization(),"票据原资格不符");
        require(!source.authorization().equals(source.previousAuthorization()),"续作必须使用独立新资格");
        authorization(input.originalPlan().authorization(),source);authorization(input.plan().authorization(),source);
    }
    private void authorization(TemporaryStore.Stored a,TemporaryRoundFacts.Source source) {
        require(a.deviceId().equals(source.device()) && a.userId().equals(source.operator())
            && a.warehouseId().equals(source.warehouse()) && a.recoveryEpoch().equals(source.epoch()),"续作资格身份或恢复代次不符");
    }
    private void fixedPlan(Input input,JsonNode body) {
        var original=input.original();var oldPlan=input.originalPlan().body();var plan=input.plan().body();
        equal(body.get("temporaryOrder"),plan,"新轮计划与正文不符");equal(original.get("temporaryOrder"),oldPlan,"前驱计划与原文不符");
        require(plan.isObject() && oldPlan.isObject() && plan.size()==oldPlan.size(),"续作原计划字段不符");
        oldPlan.fields().forEachRemaining(field->{
            if(!List.of("authorizationId","clientOrderId").contains(field.getKey()))equal(field.getValue(),plan.get(field.getKey()),"续作不得改写原计划");
        });
        equal(text(plan,"authorizationId"),input.source().authorization().toString(),"续作计划新资格不符");
        equal(text(plan,"clientOrderId"),input.source().client().toString(),"续作计划新来源不符");
        for(String field:List.of("inventorySnapshotId","policyVersion","autoDeductInventory","orderRevision"))
            equal(body.get(field),original.get(field),"续作原基线或政策不符");
        equal(text(body,"planSha256"),sha(plan),"续作计划摘要不符");
    }
    public record Input(SubmissionStore.Head current,SubmissionStore.Head previous,TemporarySubmissionPlan.Plan plan,
        TemporarySubmissionPlan.Plan originalPlan,JsonNode original,TemporaryRoundFacts.Source source,
        List<TemporaryRetainedManifest.Expected> retained) {
        public Input { retained=List.copyOf(retained); }
    }
}
