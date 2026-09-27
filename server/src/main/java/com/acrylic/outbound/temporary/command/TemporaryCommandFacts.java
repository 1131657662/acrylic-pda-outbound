package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only precondition facts for a temporary command. It returns exactly what a client must echo back in
 * {@code expected}, plus the six bound scalars and the source identity; it grants nothing. No write capability,
 * no decision, no release, and every capability flag stays false. Visibility is limited to the two audiences of
 * this chain ({@code temporary_order:confirm} reviewers and {@code temporary_order:link} supervisors) and to the
 * caller's own warehouse.
 */
@Service
@Transactional(readOnly=true)
public class TemporaryCommandFacts {
    private static final List<String> BOUND=List.of("warehouseId","submissionId","workRound","authorizationId",
        "freezeDeviceId","sourceRecoveryEpoch");

    private final CommandIdentity identities;private final CommandFacts facts;
    public TemporaryCommandFacts(CommandIdentity identities,CommandFacts facts) {
        this.identities=identities;this.facts=facts;
    }

    public ObjectNode read(UUID warehouse,UUID submission) {
        var actor=identities.current(warehouse);
        if(!actor.permissions().contains("temporary_order:confirm") && !actor.permissions().contains("temporary_order:link"))
            throw CommandErrors.missing();
        var context=facts.read(submission);
        if(!warehouse.equals(context.id("warehouseId")))throw CommandErrors.missing();
        var result=SubmissionJson.object();
        result.set("expected",CommandExpected.current(context,actor.authVersion()));
        BOUND.forEach(key->result.set(key,context.json().get(key)));
        result.set("source",SubmissionJson.object().put("kind","TEMP")
            .put("creatorDeviceId",context.text("creatorDeviceId")).put("clientOrderId",context.text("clientOrderId")));
        return result;
    }
}
