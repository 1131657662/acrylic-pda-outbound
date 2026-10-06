package com.acrylic.outbound.temporary.continuation;

import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.command.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Attaches settlement to the same command receipt identity before the outer transaction commits. */
@Component
@Transactional(propagation=Propagation.MANDATORY)
public class TemporaryRoundCompletion {
    private final TemporaryRoundFacts facts;private final TemporaryContinuationLifecycleStore lifecycle;
    public TemporaryRoundCompletion(TemporaryRoundFacts facts,TemporaryContinuationLifecycleStore lifecycle) { this.facts=facts;this.lifecycle=lifecycle; }
    public void complete(CommandWrite write,TemporaryConfirmationSource.Value value,JsonNode receipt) {
        var head=value.head();if(head.control().round()==1)return;
        var request=TemporaryRoundControl.read(value.body());
        equal(request.predecessor(),facts.reference(head.control().id()),"续作结算绑定不符");
        var source=facts.read(request,new SubmissionAccess.Actor(head.actor(),head.warehouse(),""));
        lifecycle.complete(new TemporaryContinuationLifecycleStore.Completion(request.predecessor().ticket(),source.scope(),source.fromRound(),
            source.authorization(),head.control().id(),write.receiptId(),canonical(receipt),write.recordedAt()));
    }
}
