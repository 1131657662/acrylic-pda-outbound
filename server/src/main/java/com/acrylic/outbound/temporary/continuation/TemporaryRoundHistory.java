package com.acrylic.outbound.temporary.continuation;

import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Rebuilds earlier qualification boundaries from immutable registrations and hash-checked originals. */
@Component
public class TemporaryRoundHistory {
    private final TemporaryRoundFacts facts;private final TemporaryRoundPredecessor predecessor;
    private final SubmissionStore submissions;private final TemporarySubmissionPlan plans;
    private final TemporaryRoundEvidence evidence;private final TemporarySubmissionSnapshot snapshots;
    public TemporaryRoundHistory(TemporaryRoundFacts facts,TemporaryRoundPredecessor predecessor,SubmissionStore submissions,
        TemporarySubmissionPlan plans,TemporaryRoundEvidence evidence,TemporarySubmissionSnapshot snapshots) {
        this.facts=facts;this.predecessor=predecessor;this.submissions=submissions;
        this.plans=plans;this.evidence=evidence;this.snapshots=snapshots;
    }
    public TemporaryEvidenceOrigins read(TemporaryRoundEvidence.Input next) {
        var frames=new ArrayList<Frame>();
        var head=next.previous();var body=next.original();var plan=next.originalPlan();
        while(head.control().round()>1) {
            var request=TemporaryRoundControl.read(body);
            equal(request.predecessor(),facts.reference(head.control().id()),"历史续作不可变引用不符");
            var source=facts.read(request,new SubmissionAccess.Actor(head.actor(),head.warehouse(),""));
            equal(source.scope(),next.source().scope(),"历史续作范围不符");
            var original=predecessor.read(request,source);var previous=submissions.required(source.previous());
            var originalPlan=plans.validate(previous,original);
            frames.add(new Frame(new TemporaryRoundEvidence.Input(head,previous,plan,originalPlan,original,source,
                facts.retained(request,source)),body));
            head=previous;body=original;plan=originalPlan;
        }
        var origins=TemporaryEvidenceOrigins.initial(head,plan);snapshots.validate(plan,body);
        for(var frame:frames.reversed()) {
            origins=evidence.validate(frame.input(),frame.body(),origins);
            snapshots.validate(frame.input().plan(),frame.body());
        }
        return origins;
    }
    private record Frame(TemporaryRoundEvidence.Input input,JsonNode body) { }
}
