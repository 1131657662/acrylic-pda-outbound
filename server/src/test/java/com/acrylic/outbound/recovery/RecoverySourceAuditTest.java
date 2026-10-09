package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoverySourceAuditTest {
    @ParameterizedTest @ValueSource(strings = {"FORMAL", "TEMP"})
    void preservesEveryOriginalIdAndSequenceAcrossSignedPrefixAndTail(String kind) throws Exception {
        var f = new RecoveryAuditFixture(kind); f.archivePrefix();

        var original = f.verifier().verify(f.bytes(), f.context());

        assertThat(original).hasSize(12);
        for (int index = 0; index < original.size(); index++)
            assertThat(original.get(index)).isEqualTo(f.events.get(index));
        ((ObjectNode)original.getFirst()).put("detail", "caller mutation");
        assertThat(f.verifier().verify(f.bytes(), f.context()).getFirst().path("detail").asText()).isEqualTo("original detail");
    }

    @ParameterizedTest @ValueSource(strings = {"FORMAL", "TEMP"})
    void acceptsAFullOriginalDeviceRangeWithoutAnArchive(String kind) throws Exception {
        var f = new RecoveryAuditFixture(kind);

        assertThat(f.verifier().verify(f.bytes(), f.context())).hasSize(12);
    }

    @Test void refusesMissingTailEvent() throws Exception {
        var f = new RecoveryAuditFixture(); f.body.withArray("auditEvents").remove(3);

        assertThatThrownBy(() -> f.verifier().verify(f.bytes(), f.context())).hasMessage("RECOVERY_AUDIT_COVERAGE");
    }

    @Test void refusesReorderedDeviceSequences() throws Exception {
        var f = new RecoveryAuditFixture(); var tail = f.body.withArray("auditEvents");
        var first = tail.get(0).deepCopy(); tail.set(0, tail.get(1).deepCopy()); tail.set(1, first);

        assertThatThrownBy(() -> f.verifier().verify(f.bytes(), f.context())).hasMessage("RECOVERY_AUDIT_SEQUENCE");
    }

    @Test void refusesDuplicateEventIdentityAtAnotherSequence() throws Exception {
        var f = new RecoveryAuditFixture(); var tail = f.body.withArray("auditEvents");
        ((ObjectNode)tail.get(1)).set("id", tail.get(0).path("id"));

        assertThatThrownBy(() -> f.verifier().verify(f.bytes(), f.context())).hasMessage("RECOVERY_AUDIT_ID_CONFLICT");
    }

    @ParameterizedTest @ValueSource(strings = {"operatorId", "originDeviceId", "assignmentId"})
    void refusesUnknownOrSubstitutedOriginalSources(String field) throws Exception {
        var f = new RecoveryAuditFixture(); ((ObjectNode)f.body.at("/auditEvents/0")).put(field, UUID.randomUUID().toString());

        assertThatThrownBy(() -> f.verifier().verify(f.bytes(), f.context())).hasMessage("RECOVERY_AUDIT_ORIGIN");
    }

    @Test void refusesModifiedCurrentEventUnderTheSameId() throws Exception {
        var f = new RecoveryAuditFixture(); ((ObjectNode)f.body.at("/events/0")).put("detail", "changed original");

        assertThatThrownBy(() -> f.verifier().verify(f.bytes(), f.context())).hasMessage("RECOVERY_AUDIT_CURRENT_CONFLICT");
    }

    @Test void refusesOmittingAnEventOfTheCurrentQualification() throws Exception {
        var f = new RecoveryAuditFixture(); f.body.withArray("events").remove(0);

        assertThatThrownBy(() -> f.verifier().verify(f.bytes(), f.context())).hasMessage("RECOVERY_AUDIT_CURRENT_MISSING");
    }

    @Test void doesNotTreatAnInventedTerminalAsAnOriginalStop() throws Exception {
        var f = new RecoveryAuditFixture(); f.body.put("recoveryPackageId", UUID.randomUUID().toString());

        assertThatThrownBy(() -> f.verifier().verify(f.bytes(), f.context())).hasMessage("RECOVERY_AUDIT_TERMINAL");
    }

    @Test void refusesAPrefixClaimWithoutTheOriginalSignedBytes() throws Exception {
        var f = new RecoveryAuditFixture(); ((ObjectNode)f.body.at("/auditCoverage/0")).put("afterSeq", "4");

        assertThatThrownBy(() -> f.verifier().verify(f.bytes(), f.context())).hasMessage("RECOVERY_AUDIT_PREFIX_GAP");
    }

    @Test void refusesACorruptedArchivedOriginal() throws Exception {
        var f = new RecoveryAuditFixture(); f.archivePrefix(); var segment = f.prefix.getFirst();
        byte[] bytes = segment.payload(); bytes[0] ^= 1; f.setPrefix((ObjectNode)segment.proof(), bytes);

        assertThatThrownBy(() -> f.verifier().verify(f.bytes(), f.context())).hasMessage("RECOVERY_AUDIT_ARCHIVE_CONTENT");
    }

    @Test void refusesAnArchiveReferenceDifferentFromThePinnedOriginal() throws Exception {
        var f = new RecoveryAuditFixture(); f.archivePrefix(); ((ObjectNode)f.body.at("/auditArchives/0")).put("throughSeq", "5");

        assertThatThrownBy(() -> f.verifier().verify(f.bytes(), f.context())).hasMessage("RECOVERY_AUDIT_ARCHIVE_REFERENCE");
    }

    @Test void refusesUnsignedChangesToTheRetainedArchiveProof() throws Exception {
        var f = new RecoveryAuditFixture(); f.archivePrefix(); var segment = f.prefix.getFirst();
        var proof = (ObjectNode)segment.proof(); proof.put("localDeletionAllowed", true); f.setPrefix(proof, segment.payload());

        assertThatThrownBy(() -> f.verifier().verify(f.bytes(), f.context())).hasMessage("RECOVERY_AUDIT_ARCHIVE_CONTENT");
    }

    @Test void preservesTheSharedDeviceSequenceAcrossFormalAndTemporaryOrigins() throws Exception {
        var f = new RecoveryAuditFixture(); var current = f.source.identity;
        var prior = new RecoverySourceIdentity("TEMP", current.warehouseId(), UUID.randomUUID(), UUID.randomUUID(),
            current.deviceId(), current.operatorId(), UUID.randomUUID(), null, current.deviceId(), UUID.randomUUID(),
            current.sourceRecoveryEpoch(), 1, 0, 1, 0);
        var event = (ObjectNode)f.events.get(0); event.remove("assignmentId");
        event.put("authorizationId", prior.qualificationId().toString()).putObject("source").put("kind", "TEMP")
            .put("creatorDeviceId", prior.creatorDeviceId().toString()).put("clientOrderId", prior.clientOrderId().toString());
        f.body.set("events", f.events.deepCopy()); f.archivePrefix();
        var context = new RecoverySourceAudit.Context(current,
            Map.of(current.qualificationId(), current, prior.qualificationId(), prior), f.prefix);

        assertThat(f.verifier().verify(f.bytes(), context)).hasSize(12);
        assertThatThrownBy(() -> f.verifier().verify(f.bytes(), f.context())).hasMessage("RECOVERY_AUDIT_ORIGIN");
    }
}
