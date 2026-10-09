package com.acrylic.outbound.recovery;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RecoveryClosedJournalTest {
    @Test void deletingTheEntireJournalCannotTurnAUsedAuthorityIntoAFreshAuthority() throws Exception {
        var supply = completed();
        Files.move(supply.root.resolve("transactions"), supply.root.resolve("retained-transactions"));
        assertThatThrownBy(supply::authority).hasMessage("CONTROL_CLOSED_TRANSACTION_JOURNAL");
    }

    @Test void deletingBothFilesOfTheCompletedTailIsDetectedAcrossRestart() throws Exception {
        var supply = completed();
        Files.move(supply.root.resolve("transactions/000000000000000001.start.json"), supply.root.resolve("retained-start.json"));
        Files.move(supply.root.resolve("transactions/000000000000000001.complete.json"), supply.root.resolve("retained-complete.json"));
        assertThatThrownBy(supply::authority).hasMessage("CONTROL_CLOSED_TRANSACTION_JOURNAL");
    }

    @Test void replacingTheWitnessWithIdenticalBytesDoesNotResetItsPinnedIdentity() throws Exception {
        var supply = completed(); Path witness = supply.root.resolve("transaction-witness.bin");
        Files.move(witness, supply.root.resolve("retained-witness.bin"));
        Files.copy(supply.root.resolve("retained-witness.bin"), witness);
        assertThatThrownBy(supply::authority).hasMessage("CONTROL_CLOSED_WITNESS_IDENTITY");
    }

    @Test void inPlaceWitnessTruncationConflictsWithRetainedJournal() throws Exception {
        var supply = completed(); Path witness = supply.root.resolve("transaction-witness.bin");
        Files.copy(witness, supply.root.resolve("retained-witness.bin"));
        try (var channel = java.nio.channels.FileChannel.open(witness, StandardOpenOption.WRITE)) { channel.truncate(32); }
        assertThatThrownBy(supply::authority).hasMessage("CONTROL_CLOSED_TRANSACTION_JOURNAL");
    }

    @Test void tamperingWithACompleteOutcomeCannotReuseTheSession() throws Exception {
        var supply = completed(); Path file = supply.root.resolve("transactions/000000000000000001.complete.json");
        Files.copy(file, supply.root.resolve("retained-completion.json"));
        var complete = (com.fasterxml.jackson.databind.node.ObjectNode)RecoveryControlHistory.read(file);
        complete.put("outcome", "COMMITTED"); RecoveryControlFixture.write(file, complete);
        assertThatThrownBy(supply::authority).hasMessage("CONTROL_CLOSED_TRANSACTION_JOURNAL");
    }

    @Test void partialWitnessAppendRefusesRestart() throws Exception {
        var supply = completed();
        Files.write(supply.root.resolve("transaction-witness.bin"), new byte[]{1}, StandardOpenOption.APPEND);
        assertThatThrownBy(supply::authority).hasMessage("CONTROL_CLOSED_TRANSACTION_JOURNAL");
    }

    @Test void completedOperationsCanBeReopenedAndAppendedWithoutLosingThePrefix() throws Exception {
        var supply = completed(); var authority = supply.authority();
        var second = RecoveryControlLock.locked(supply.location.root(), authority::beginLocked);
        assertThat(second.ordinal()).isEqualTo(2); authority.complete(second, 0);
        assertThat(supply.authority().read()).isEqualTo(authority.control());
    }

    private static RecoveryClosedTestSupply completed() throws Exception {
        var supply = RecoveryClosedTestSupply.of(RecoverySourceFixture.create()); var authority = supply.authority();
        var operation = RecoveryControlLock.locked(supply.location.root(), authority::beginLocked);
        authority.complete(operation, 1); return supply;
    }
}
