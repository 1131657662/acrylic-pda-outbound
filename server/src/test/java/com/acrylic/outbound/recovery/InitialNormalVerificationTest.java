package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class InitialNormalVerificationTest {
    @TempDir Path directory;

    @Test void completeFileWithoutDatabaseIsDenied() throws Exception {
        var fixture = new InitialNormalFixture(directory); fixture.store.prepare(fixture.original); fixture.complete();
        var result = RecoveryAdmissionVerifier.verifyInitial(evidence(null), fixture.store, directory.resolve("closed"));
        assertThat(result.admitted()).isFalse();
        assertThat(result.gaps()).containsExactly("INITIAL_NORMAL_DATABASE_MISSING");
    }

    @Test void databaseWithoutCompletedFileIsDenied() throws Exception {
        var fixture = new InitialNormalFixture(directory); fixture.store.prepare(fixture.original);
        var result = RecoveryAdmissionVerifier.verifyInitial(evidence(db(fixture)), fixture.store, directory.resolve("closed"));
        assertThat(result.admitted()).isFalse();
        assertThat(result.gaps()).containsExactly("INITIAL_NORMAL_COMPLETION_MISSING");
    }

    @Test void bothSidesAgreeWithoutGrantingCapabilities() throws Exception {
        var fixture = new InitialNormalFixture(directory); fixture.store.prepare(fixture.original); fixture.complete();
        var result = RecoveryAdmissionVerifier.verifyInitial(evidence(db(fixture)), fixture.store, directory.resolve("closed"));
        assertThat(result.admitted()).isTrue();
        assertThat(result.ordinaryWritesAllowed()).isFalse(); assertThat(result.localDeletionAllowed()).isFalse();
    }

    @Test void anyClosedBootstrapBlocksInitialEvidence() throws Exception {
        var fixture = new InitialNormalFixture(directory); fixture.store.prepare(fixture.original); fixture.complete();
        Files.createDirectory(directory.resolve("closed"));
        var result = RecoveryAdmissionVerifier.verifyInitial(evidence(db(fixture)), fixture.store, directory.resolve("closed"));
        assertThat(result.gaps()).containsExactly("INITIAL_NORMAL_CLOSED_AUTHORITY");
    }

    @Test void databaseIdentityMappingAndClosedHistoryConflictsAreDenied() throws Exception {
        var fixture = new InitialNormalFixture(directory); fixture.store.prepare(fixture.original); fixture.complete();
        var db = db(fixture);
        var conflicts = java.util.List.of(
            new RecoveryAdmissionEvidence.Initial(UUID.randomUUID(), db.schema(), db.state(), false, db.request()),
            new RecoveryAdmissionEvidence.Initial(db.serverUuid(), "other", db.state(), false, db.request()),
            new RecoveryAdmissionEvidence.Initial(db.serverUuid(), db.schema(), new RecoveryAdmissionEvidence.State("NORMAL", 2), false, db.request()),
            new RecoveryAdmissionEvidence.Initial(db.serverUuid(), db.schema(), new RecoveryAdmissionEvidence.State("RECOVERY_PENDING", 1), false, db.request()),
            new RecoveryAdmissionEvidence.Initial(db.serverUuid(), db.schema(), db.state(), true, db.request()));
        for (var conflict : conflicts) assertThat(RecoveryAdmissionVerifier.verifyInitial(evidence(conflict),
            fixture.store, directory.resolve("closed")).admitted()).isFalse();
    }

    @Test void durableUnknownFencesFreshReader() throws Exception {
        var fixture = new InitialNormalFixture(directory); fixture.store.prepare(fixture.original);
        fixture.store.locked(() -> { fixture.store.unknownLocked(); return null; });
        assertThatThrownBy(fixture.store::inspect).hasMessage("INITIAL_NORMAL_UNKNOWN");
        assertThat(RecoveryAdmissionVerifier.verifyInitial(evidence(db(fixture)), fixture.store,
            directory.resolve("closed")).admitted()).isFalse();
    }

    private static RecoveryAdmissionEvidence.Initial db(InitialNormalFixture fixture) {
        var original = InitialNormalEvidence.parse(fixture.original);
        return new RecoveryAdmissionEvidence.Initial(original.serverUuid(), original.schema(),
            new RecoveryAdmissionEvidence.State("NORMAL", original.numericEpoch()), false, original);
    }

    private static RecoveryAdmissionEvidence evidence(RecoveryAdmissionEvidence.Initial initial) {
        return new RecoveryAdmissionEvidence() {
            public Initial initial() { return initial; }
            public Binding binding() { throw new AssertionError("closed path"); }
            public Admission admission() { throw new AssertionError("closed path"); }
            public Projection projection() { throw new AssertionError("closed path"); }
            public State state() { throw new AssertionError("closed path"); }
        };
    }
}
