package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class HandoverTemporaryActivationBindingTest {
    @Test void intactHighRoundPrefixAndOriginalSourceAreAccepted() {
        var f = new HandoverTemporaryActivationFixture(); var ticket = f.ticket();
        HandoverTemporaryActivation.verifyTicket(ticket, f.facts);
        HandoverTemporaryActivationGuard.requireSource(ticket, f.body, f.stop);
        HandoverTemporaryActivationGuard.requireInput(f.command(), ticket);
    }

    @ParameterizedTest @ValueSource(strings = {"scopeId", "submissionId", "canonicalOrderId", "targetDeviceId", "targetUserId", "preparedTicketSha256"})
    void alteredContextCannotConsumeFrozenTicket(String field) {
        var f = new HandoverTemporaryActivationFixture(); var facts = f.facts.deepCopy();
        facts.put(field, field.endsWith("Sha256") ? "c".repeat(64) : java.util.UUID.randomUUID().toString());
        expect("HANDOVER_TEMP_TICKET_CHANGED", () -> HandoverTemporaryActivation.verifyTicket(f.ticket(), facts));
    }

    @ParameterizedTest @ValueSource(strings = {"fromWorkRound", "toWorkRound", "inputHighWatermark"})
    void changedRoundOrHighWatermarkCannotMoveOwners(String field) {
        var f = new HandoverTemporaryActivationFixture(); var facts = f.facts.deepCopy().put(field, "999");
        expect("HANDOVER_TEMP_TICKET_CHANGED", () -> HandoverTemporaryActivation.verifyTicket(f.ticket(), facts));
    }

    @Test void alteredPrefixAndMutableTicketBytesAreRejectedOrIsolated() {
        var f = new HandoverTemporaryActivationFixture();
        expect("HANDOVER_TEMP_TICKET_CHANGED", () -> HandoverTemporaryActivation.verifyTicket(f.ticketWithPrefix("a".repeat(64)), f.facts));
        var ticket = f.ticket(); ticket.ticketBytes()[0] = 0;
        HandoverTemporaryActivation.verifyTicket(ticket, f.facts);
    }

    @Test void changedSourceAndEvidenceSetCannotUseIntactTicket() {
        var f = new HandoverTemporaryActivationFixture(); var body = f.body.deepCopy().put("sourceRecoveryEpoch", "2");
        expect("HANDOVER_TEMP_SOURCE_CHANGED", () -> HandoverTemporaryActivationGuard.requireSource(f.ticket(), body, f.stop));
        var stop = f.stop.deepCopy().put("sourceEvidenceSetId", java.util.UUID.randomUUID().toString());
        expect("HANDOVER_TEMP_SOURCE_CHANGED", () -> HandoverTemporaryActivationGuard.requireSource(f.ticket(), f.body, stop));
    }

    @Test void unknownCapabilityAndDifferentRecipientCannotActivate() {
        var f = new HandoverTemporaryActivationFixture();
        expect("INVALID_HANDOVER_COMMAND", () -> HandoverTemporaryActivationGuard.requireInput(f.command().put("mayResumeScanning", true), f.ticket()));
        expect("HANDOVER_CONTEXT_CHANGED", () -> HandoverTemporaryActivationGuard.requireInput(
            f.command().put("targetUserId", java.util.UUID.randomUUID().toString()), f.ticket()));
    }

    private static void expect(String code, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class, failure -> assertThat(failure.code()).isEqualTo(code));
    }
}
