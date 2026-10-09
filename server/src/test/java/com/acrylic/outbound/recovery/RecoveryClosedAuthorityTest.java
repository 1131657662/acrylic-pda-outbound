package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoveryClosedAuthorityTest {
    @Test void bindsExplicitIndependentAuthorityAndSourcePins() throws Exception {
        var source = RecoverySourceFixture.initial("FORMAL"); var supply = RecoveryClosedTestSupply.of(source);
        var authority = supply.authority();

        assertThat(authority.read()).isEqualTo(source.control.read());
        assertThat(authority.sourceIdentity()).isEqualTo(source.identity);
        assertThat(authority.sourcePinSha256()).isEqualTo(source.pin);
        assertThat(authority.ordinaryWritesAllowed()).isFalse();
        assertThat(authority.localDeletionAllowed()).isFalse();
    }

    @ParameterizedTest @ValueSource(strings={"INITIAL_NORMAL_INSTALL", "NORMAL", "CLOSED_AUTHORITY", ""})
    void rejectsNormalOrUnknownAuthorityKinds(String kind) throws Exception {
        var supply = RecoveryClosedTestSupply.of(RecoverySourceFixture.initial("TEMP"));
        supply.identity.put("authorityKind", kind); supply.publish();

        assertThatThrownBy(supply::authority).hasMessage("CONTROL_CLOSED_AUTHORITY_KIND_FORBIDDEN");
    }

    @ParameterizedTest @ValueSource(strings={"installationId", "recoverySessionId", "epochId"})
    void rejectsForeignControlIdentity(String field) throws Exception {
        var supply = RecoveryClosedTestSupply.of(RecoverySourceFixture.create());
        supply.identity.put(field, UUID.randomUUID().toString()); supply.publish();

        assertThatThrownBy(supply::authority).hasMessage(field.equals("installationId")
            ? "CONTROL_CLOSED_INSTALLATION_MISMATCH" : "CONTROL_CLOSED_SESSION_MISMATCH");
    }

    @ParameterizedTest @ValueSource(strings={"controlRevision", "controlRecordSha256", "previousRecordSha256"})
    void rejectsDifferentHeadEvenWithItsOwnValidPin(String field) throws Exception {
        var supply = RecoveryClosedTestSupply.of(RecoverySourceFixture.create());
        supply.identity.put(field, field.equals("controlRevision") ? "3" : "f".repeat(64)); supply.publish();

        assertThatThrownBy(supply::authority).hasMessage("CONTROL_CLOSED_HEAD_MISMATCH");
    }

    @Test void sourceIdentityAndItsIndependentPinMustBothMatch() throws Exception {
        var supply = RecoveryClosedTestSupply.of(RecoverySourceFixture.create());
        supply.identity.put("sourcePinSha256", "f".repeat(64)); supply.publish();
        assertThatThrownBy(supply::authority).hasMessage("CONTROL_CLOSED_SOURCE_PIN_MISMATCH");
        supply.identity.put("sourcePinSha256", supply.source.manifestSha256());
        ((ObjectNode)supply.identity.path("source")).put("submissionId", UUID.randomUUID().toString()); supply.publish();
        assertThatThrownBy(supply::authority).hasMessage("CONTROL_CLOSED_SOURCE_MISMATCH");
    }

    @Test void rechecksPinnedBytesInsteadOfKeepingCachedTrust() throws Exception {
        var supply = RecoveryClosedTestSupply.of(RecoverySourceFixture.create()); var authority = supply.authority();
        supply.identity.put("controlRevision", "3"); supply.publish();

        assertThatThrownBy(authority::read).hasMessage("RECOVERY_CLOSED_AUTHORITY_PIN_MISMATCH");
    }

    @Test void refusesControlAdvanceAndUnknownCompletionAtRestart() throws Exception {
        var source = RecoverySourceFixture.create(); var supply = RecoveryClosedTestSupply.of(source);
        var authority = supply.authority(); source.control.append(source.control.next("VERIFYING"));
        assertThatThrownBy(authority::read).hasMessage("CONTROL_CLOSED_HEAD_MISMATCH");
        var current = RecoveryClosedTestSupply.of(source); current.authority().fenceUnknown();
        assertThatThrownBy(current::authority).hasMessage("CONTROL_TRANSACTION_OUTCOME_UNCONFIRMED");
    }

    @Test void unknownOrMissingFieldsAreNeverDefaulted() throws Exception {
        var supply = RecoveryClosedTestSupply.of(RecoverySourceFixture.create());
        supply.identity.put("ordinaryWritesAllowed", true); supply.publish();
        assertThatThrownBy(supply::authority).hasMessage("CONTROL_FIELDS");
        supply.identity.remove("ordinaryWritesAllowed"); supply.identity.remove("sourcePinSha256"); supply.publish();
        assertThatThrownBy(supply::authority).hasMessage("CONTROL_FIELDS");
    }

    @Test void doesNotInferPinsFromTheAuthorityItself() throws Exception {
        var supply = RecoveryClosedTestSupply.of(RecoverySourceFixture.create());
        assertThatThrownBy(() -> new RecoveryClosedAuthority(supply.location, supply.checkpoint,
            new RecoveryClosedAuthority.Pins(supply.root, null, supply.source)))
            .hasMessage("RECOVERY_CLOSED_AUTHORITY_PIN_REQUIRED");
        assertThatThrownBy(() -> new RecoveryClosedAuthority(supply.location, supply.checkpoint,
            new RecoveryClosedAuthority.Pins(supply.root, supply.pin, new RecoveryClosedAuthority.Source(null, supply.source.identity()))))
            .hasMessage("RECOVERY_CLOSED_SOURCE_PIN_REQUIRED");
    }

    @Test void closedAuthorityCannotLiveWithinAnyRestoredRootOrControlTree() throws Exception {
        var supply = RecoveryClosedTestSupply.of(RecoverySourceFixture.create());
        for (Path root : new Path[]{supply.location.root(), supply.location.restoreRoots().getFirst()})
            assertThatThrownBy(() -> new RecoveryClosedAuthority(supply.location, supply.checkpoint,
                new RecoveryClosedAuthority.Pins(root, supply.pin, supply.source)))
                .hasMessage(root.equals(supply.location.root()) ? "CONTROL_CLOSED_INSIDE_AUTHORITY" : "CONTROL_CLOSED_INSIDE_RESTORE_SET");
    }

    @Test void unfinishedDurableTransactionRefusesANewProcessEvenWithoutTheMemoryFence() throws Exception {
        var supply = RecoveryClosedTestSupply.of(RecoverySourceFixture.create()); var authority = supply.authority();
        RecoveryControlLock.locked(supply.location.root(), authority::beginLocked);

        assertThatThrownBy(supply::authority).hasMessage("CONTROL_TRANSACTION_OUTCOME_UNCONFIRMED");
    }

    @Test void removingTheLastCompletionCannotHideAnUncertainTransaction() throws Exception {
        var supply = RecoveryClosedTestSupply.of(RecoverySourceFixture.create()); var authority = supply.authority();
        var operation = RecoveryControlLock.locked(supply.location.root(), authority::beginLocked);
        authority.complete(operation, 1); assertThat(authority.read()).isNotNull();
        Files.move(supply.root.resolve("transactions/000000000000000001.complete.json"),
            supply.root.resolve("retained-completion.json"));

        assertThatThrownBy(supply::authority).hasMessage("CONTROL_TRANSACTION_OUTCOME_UNCONFIRMED");
    }

    @Test void declaredInitialNormalControlHistoryCannotBeUsedForRecovery() throws Exception {
        var source = RecoverySourceFixture.create(); var supply = RecoveryClosedTestSupply.of(source);
        var identity = (ObjectNode)RecoveryControlHistory.read(source.control.root.resolve("identity.json"));
        identity.put("authorityKind", "INITIAL_NORMAL_INSTALL");
        RecoveryControlFixture.write(source.control.root.resolve("identity.json"), identity);

        assertThatThrownBy(supply::authority).hasMessage("CONTROL_AUTHORITY_PURPOSE_FORBIDDEN");
    }
}
