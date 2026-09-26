package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoveryControlIntegrityTest {
    @Test void fullChainRetainsTheSameContextAndNewEpochAcrossRestart() throws Exception {
        var f = RecoveryControlFixture.create();
        String firstEpoch = f.command.path("epochId").asText();
        for (String phase : List.of("RECOVERING", "VERIFYING", "RELEASE_READY")) f.append(f.next(phase));
        var result = f.read();
        assertThat(result.revision()).isEqualTo("4");
        assertThat(result.epochId().toString()).isNotEqualTo(firstEpoch);
        assertThat(result.affectedWarehouseIds()).containsExactly("W1");
        assertThat(result.localDeletionAllowed()).isFalse();
        assertThat(f.reader().read(f.checkpoint)).isEqualTo(result);
    }

    @Test void laterTrustedAnchorRejectsEntireOldAuthority() throws Exception {
        var f = RecoveryControlFixture.create();
        byte[] priorPointer = Files.readAllBytes(f.root.resolve("current.json"));
        f.append(f.next("RECOVERING")); RecoveryControlFixture.write(f.checkpoint, RecoveryControlFixture.pointer(f.latest));
        Files.move(f.root.resolve("history/000000000000000002.json"), f.root.getParent().resolve("preserved-second.json"));
        Files.write(f.root.resolve("current.json"), priorPointer);
        assertThatThrownBy(f::read).hasMessage("CONTROL_CHECKPOINT_ROLLBACK");
    }

    @Test void historyAppendWithoutPointerCommitRemainsClosedAndUntouched() throws Exception {
        var f = RecoveryControlFixture.create();
        byte[] before = Files.readAllBytes(f.root.resolve("current.json"));
        f.append(f.next("RECOVERING")); Files.write(f.root.resolve("current.json"), before);
        assertThatThrownBy(f::read).hasMessage("CONTROL_COMMAND_INCOMPLETE");
        assertThat(Files.readAllBytes(f.root.resolve("current.json"))).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings={"expectedRevision", "phase", "epochId", "snapshotId", "recoverySessionId", "commandId"})
    void fullyRehashedIllegalCommandsAreStillRejected(String field) throws Exception {
        var f = RecoveryControlFixture.create(); var next = f.next("RECOVERING");
        switch (field) {
            case "expectedRevision" -> next.put(field, "0");
            case "phase" -> next.put(field, "RELEASE_READY");
            case "epochId", "commandId" -> next.put(field, f.command.path(field).asText());
            case "snapshotId" -> next.put(field, "other");
            default -> next.put(field, UUID.randomUUID().toString());
        }
        f.append(next);
        assertThatThrownBy(f::read).isInstanceOf(RecoveryControlException.class);
    }

    @Test void forgedBootstrapHashCannotSelectADifferentInitialCommand() throws Exception {
        var f = RecoveryControlFixture.create();
        var identity = RecoveryControlFixture.JSON.readTree(Files.readAllBytes(f.root.resolve("identity.json")));
        ((com.fasterxml.jackson.databind.node.ObjectNode)identity).put("initialCommandSha256", "f".repeat(64));
        RecoveryControlFixture.write(f.root.resolve("identity.json"), identity);
        assertThatThrownBy(f::read).hasMessage("CONTROL_BOOTSTRAP_MISMATCH");
    }

    @Test void trustedCheckpointMustBeOutsideAuthorityAndRestoreSet() throws Exception {
        var f = RecoveryControlFixture.create();
        assertThatThrownBy(() -> f.reader().read(f.root.resolve("current.json"))).hasMessage("CONTROL_CHECKPOINT_INSIDE_AUTHORITY");
        Path restored = f.restoreRoot.resolve("trusted.json"); Files.copy(f.checkpoint, restored);
        assertThatThrownBy(() -> f.reader().read(restored)).hasMessage("CONTROL_CHECKPOINT_INSIDE_RESTORE_SET");
    }

    @Test void oversizedOriginalIsBoundedAndNotTruncatedIntoAValidRecord() throws Exception {
        var f = RecoveryControlFixture.create();
        Files.write(f.root.resolve("current.json"), new byte[65537]);
        assertThatThrownBy(f::read).hasMessage("CONTROL_JSON_SIZE");
    }

    @Test void missingFilesAreNeverInitialized() throws Exception {
        var f = RecoveryControlFixture.create();
        Files.move(f.root.resolve("current.json"), f.root.getParent().resolve("preserved-pointer.json"));
        assertThatThrownBy(f::read).hasMessage("CONTROL_FILE_MISSING");
        assertThat(f.root.resolve("current.json")).doesNotExist();
    }

    @Test void historyGapIsRejectedBeforeReadingAChosenSuffix() throws Exception {
        var f = RecoveryControlFixture.create();
        Files.move(f.root.resolve("history/000000000000000001.json"), f.root.resolve("history/000000000000000002.json"));
        assertThatThrownBy(f::read).hasMessage("CONTROL_HISTORY_GAP");
    }
}
