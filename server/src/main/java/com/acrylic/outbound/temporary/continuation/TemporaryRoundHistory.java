package com.acrylic.outbound.temporary.continuation;

import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.*;
import com.acrylic.outbound.handover.TemporaryHandoverHistory;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Rebuilds earlier qualification boundaries from immutable registrations and hash-checked originals. */
@Component
public class TemporaryRoundHistory {
    private final TemporaryRoundFacts facts;private final TemporaryRoundPredecessor predecessor;
    private final SubmissionStore submissions;private final TemporarySubmissionPlan plans;
    private final TemporaryRoundEvidence evidence;private final TemporarySubmissionSnapshot snapshots;
    private final TemporaryHandoverHistory handovers;
    public TemporaryRoundHistory(TemporaryRoundFacts facts,TemporaryRoundPredecessor predecessor,SubmissionStore submissions,
        TemporarySubmissionPlan plans,TemporaryRoundEvidence evidence,TemporarySubmissionSnapshot snapshots,
        TemporaryHandoverHistory handovers) {
        this.facts=facts;this.predecessor=predecessor;this.submissions=submissions;
        this.plans=plans;this.evidence=evidence;this.snapshots=snapshots;
        this.handovers=handovers;
    }
    public TemporaryEvidenceOrigins read(TemporaryRoundEvidence.Input next) {
        return read(next.previous(),next.original(),next.originalPlan(),next.source().scope());
    }
    public TemporaryEvidenceOrigins read(SubmissionStore.Head head,JsonNode body,TemporarySubmissionPlan.Plan plan) {
        return read(head,body,plan,null);
    }
    private TemporaryEvidenceOrigins read(SubmissionStore.Head head,JsonNode body,TemporarySubmissionPlan.Plan plan,UUID scope) {
        var frames=new ArrayList<Frame>();
        var visited=new HashSet<UUID>();
        while(head.control().round()>1) {
            require(visited.add(head.control().id()),"历史前驱链重复");
            var step=body.has("temporaryHandover")?handovers.step(head,body):continuation(head,body,plan);
            if(scope==null)scope=step.scope();
            equal(step.scope(),scope,"历史前驱范围不符");
            require(step.previous().control().round()==head.control().round()-1,"历史前驱轮次不连续");
            frames.add(new Frame(step,plan,body));
            head=step.previous();body=step.original();plan=step.oldPlan();
        }
        equal(SubmissionControl.read(head.control().id(),body),head.control(),"历史首轮控制头不符");
        var origins=TemporaryEvidenceOrigins.initial(head,plan);snapshots.validate(plan,body);
        for(var frame:frames.reversed()) {
            origins=frame.step().validate(origins);
            snapshots.validate(frame.plan(),frame.body());
        }
        return origins;
    }
    private TemporaryHistoryStep continuation(SubmissionStore.Head head,JsonNode body,TemporarySubmissionPlan.Plan plan) {
        var request=TemporaryRoundControl.read(body);
        equal(request.control(),head.control(),"历史续作控制头不符");
        equal(request.predecessor(),facts.reference(head.control().id()),"历史续作不可变引用不符");
        var source=facts.read(request,new SubmissionAccess.Actor(head.actor(),head.warehouse(),""));
        var original=predecessor.read(request,source);var previous=submissions.required(source.previous());
        var input=new TemporaryRoundEvidence.Input(head,previous,plan,plans.validate(previous,original),original,source,
            facts.retained(request,source));
        return new Step(input,body,evidence);
    }
    private record Step(TemporaryRoundEvidence.Input input,JsonNode body,TemporaryRoundEvidence evidence) implements TemporaryHistoryStep {
        public UUID scope() { return input.source().scope(); }
        public SubmissionStore.Head previous() { return input.previous(); }
        public JsonNode original() { return input.original(); }
        public TemporarySubmissionPlan.Plan oldPlan() { return input.originalPlan(); }
        public TemporaryEvidenceOrigins validate(TemporaryEvidenceOrigins origins) { return evidence.validate(input,body,origins); }
    }
    private record Frame(TemporaryHistoryStep step,TemporarySubmissionPlan.Plan plan,JsonNode body) { }
}
