package com.acrylic.outbound.recovery;

import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecoveryControlReaderTest {
    @Test void readsTheLatestClosedRecordAndNeverReportsWritePermission() throws Exception {
        var fixture = RecoveryControlFixture.create();

        var snapshot = fixture.read();

        assertThat(snapshot.installationId()).isEqualTo(fixture.installation);
        assertThat(snapshot.revision()).isEqualTo("1");
        assertThat(snapshot.phase()).isEqualTo("QUIESCING");
        assertThat(snapshot.snapshotId()).isEqualTo("snapshot-1");
        assertThat(snapshot.ordinaryWritesAllowed()).isFalse();
        assertThat(snapshot.localDeletionAllowed()).isFalse();
    }

    @Test void rejectsAnInstallationMismatchBeforeReturningARecord() throws Exception {
        var fixture = RecoveryControlFixture.create();
        var wrong = new RecoveryControlReader(new RecoveryControlLocation(fixture.root, java.util.UUID.randomUUID(), java.util.List.of(fixture.restoreRoot)));

        assertThatThrownBy(() -> wrong.read(fixture.checkpoint)).isInstanceOf(RecoveryControlException.class)
                .hasMessage("CONTROL_INSTALLATION_MISMATCH");
    }

    @Test void rejectsAStaleOrForgedCurrentPointer() throws Exception {
        var fixture = RecoveryControlFixture.create();
        var pointer = RecoveryControlFixture.JSON.createObjectNode().put("formatVersion", 1)
                .put("installationId", fixture.installation.toString()).put("revision", "0")
                .put("recordSha256", "c".repeat(64));
        RecoveryControlFixture.write(fixture.root.resolve("current.json"), pointer);

        assertThatThrownBy(fixture::read).isInstanceOf(RecoveryControlException.class)
                .hasMessage("CONTROL_CHECKPOINT_ROLLBACK");
    }

    @Test void rejectsAnUnknownArtifactInsteadOfGuessingTheCurrentState() throws Exception {
        var fixture = RecoveryControlFixture.create();
        Files.writeString(fixture.root.resolve("authority.tmp"), "partial");

        assertThatThrownBy(fixture::read).isInstanceOf(RecoveryControlException.class)
                .hasMessage("CONTROL_UNKNOWN_ARTIFACT");
    }

    @Test void readerCannotPassWhileSameJvmHoldsTheManagedLock() throws Exception {
        var fixture = RecoveryControlFixture.create();
        RecoveryControlLock.locked(fixture.root, () -> {
            assertThatThrownBy(fixture::read).isInstanceOf(RecoveryControlException.class)
                    .hasMessage("CONTROL_COORDINATOR_BUSY");
            return null;
        });
        assertThat(fixture.read().revision()).isEqualTo("1");
    }
}
