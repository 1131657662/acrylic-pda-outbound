package com.acrylic.outbound.recovery;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RecoveryWriterLeaseTest {
    @Test void legacyAuthorityIsReadableButCannotFabricateWriterAdmission() throws Exception {
        var fixture = RecoveryControlFixture.create();
        assertThat(fixture.read().revision()).isEqualTo("1");
        assertThatThrownBy(() -> acquire(fixture)).hasMessage("CONTROL_WRITER_PROTOCOL_REQUIRED");
        assertThat(fixture.root.resolve("writers.lock")).doesNotExist();
    }

    @Test void protocolIsBoundToTheOriginalInstallationIdentity() throws Exception {
        var fixture = protocol();
        var descriptor = RecoveryControlHistory.read(fixture.root.resolve("writers.json")).deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode)descriptor).put("identitySha256", "a".repeat(64));
        RecoveryControlFixture.write(fixture.root.resolve("writers.json"), descriptor);
        assertThatThrownBy(() -> acquire(fixture)).hasMessage("CONTROL_WRITER_PROTOCOL_MISMATCH");
    }

    @Test void missingLockFailsWithoutCreatingIt() throws Exception {
        var fixture = protocol();
        Files.move(fixture.root.resolve("writers.lock"), fixture.root.getParent().resolve("retained-lock"));
        assertThatThrownBy(() -> acquire(fixture)).hasMessage("CONTROL_FILE_MISSING");
        assertThat(fixture.root.resolve("writers.lock")).doesNotExist();
    }

    @Test void wrongLockLengthCannotBeAdmitted() throws Exception {
        var fixture = protocol();
        Files.writeString(fixture.root.resolve("writers.lock"), "00");
        assertThatThrownBy(() -> acquire(fixture)).hasMessage("CONTROL_WRITER_LOCK_INVALID");
    }

    @Test void coordinatorMustBeHeldBeforeWriterAdmission() throws Exception {
        var fixture = protocol();
        assertThatThrownBy(() -> RecoveryWriterLease.acquire(fixture.root)).hasMessage("CONTROL_COORDINATOR_REQUIRED");
    }

    @Test void readingProtocolDoesNotPreventNormalLeaseClosure() throws Exception {
        var fixture = protocol();
        try (var first = acquire(fixture); var second = acquire(fixture)) {
            assertThat(fixture.read().revision()).isEqualTo("1");
            second.close(); second.close();
            assertThat(fixture.read().ordinaryWritesAllowed()).isFalse();
        }
        try (var reopened = acquire(fixture)) { assertThat(fixture.read().revision()).isEqualTo("1"); }
    }

    static RecoveryControlFixture protocol() throws Exception {
        var fixture = RecoveryControlFixture.create();
        var identity = RecoveryControlHistory.read(fixture.root.resolve("identity.json"));
        Files.writeString(fixture.root.resolve("writers.lock"), "0");
        Files.createDirectory(fixture.root.resolve("intents"));
        RecoveryControlFixture.write(fixture.root.resolve("admission.json"), RecoveryControlFixture.JSON.createObjectNode()
            .put("formatVersion", 1).put("installationId", fixture.installation.toString())
            .put("targetRevision", "1").put("intentSha256", RecoveryControlFormat.ZERO_HASH));
        var descriptor = RecoveryControlFixture.JSON.createObjectNode().put("formatVersion", 2)
            .put("installationId", fixture.installation.toString()).put("identitySha256", RecoveryControlFormat.hash(identity))
            .put("fileIdentity", RecoveryWriterProtocol.fileIdentity(fixture.root.resolve("writers.lock")));
        RecoveryControlFixture.write(fixture.root.resolve("writers.json"), descriptor);
        return fixture;
    }

    private static RecoveryWriterLease acquire(RecoveryControlFixture fixture) throws Exception {
        return RecoveryControlLock.locked(fixture.root, () -> RecoveryWriterLease.acquire(fixture.root));
    }
}
