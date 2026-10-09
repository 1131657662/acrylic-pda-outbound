package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoverySourceReaderTest {
    @Test void readsPinnedFilesUnderActualClosedControlWithoutGrantingPermission() throws Exception {
        var f = RecoverySourceFixture.create();

        var source = f.read();

        assertThat(source.identity()).isEqualTo(f.identity);
        assertThat(source.control()).isEqualTo(f.control.read());
        assertThat(source.manifestSha256()).isEqualTo(f.pin);
        for (String kind : RecoverySourceManifest.OBJECTS)
            assertThat(source.original(kind)).isEqualTo(Files.readAllBytes(f.object(kind)));
        assertThat(source.ordinaryWritesAllowed()).isFalse();
        assertThat(source.localDeletionAllowed()).isFalse();
    }

    @Test void retainsTemporarySourceWithoutInventingCanonicalOrder() throws Exception {
        var f = RecoverySourceFixture.temporary();

        assertThat(f.read().identity().canonicalOrderId()).isNull();
        assertThat(f.read().identity().sourceKind()).isEqualTo("TEMP");
    }

    @Test void callersCannotRewritePinnedOriginalBytes() throws Exception {
        var f = RecoverySourceFixture.create(); var source = f.read();
        byte[] returned = source.original("payload"); returned[0] ^= 1;

        assertThat(source.original("payload")).isEqualTo(Files.readAllBytes(f.object("payload")));
        assertThatThrownBy(() -> source.original("deviceAck")).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"origin", "payload", "result", "occupancy", "permission"})
    void refusesMissingOriginalInsteadOfReturningPartialEvidence(String kind) throws Exception {
        var f = RecoverySourceFixture.create();
        Files.move(f.object(kind), f.object(kind).resolveSibling(kind + "-preserved.bin"));

        assertThatThrownBy(f::read).isInstanceOf(RecoveryControlException.class).hasMessage("CONTROL_FILE_MISSING");
    }

    @ParameterizedTest @ValueSource(strings = {"origin", "payload", "result", "occupancy", "permission"})
    void rechecksBytesOnEveryCallAfterAnEarlierRead(String kind) throws Exception {
        var f = RecoverySourceFixture.create(); f.read();
        byte[] bytes = Files.readAllBytes(f.object(kind)); bytes[0] ^= 1; Files.write(f.object(kind), bytes);

        assertThatThrownBy(f::read).isInstanceOf(RecoveryControlException.class).hasMessage("RECOVERY_SOURCE_OBJECT_MISMATCH");
    }

    @Test void rejectsTamperedManifestEvenWhenItIsStillCanonical() throws Exception {
        var f = RecoverySourceFixture.create(); f.manifest.put("snapshotId", "different-snapshot");
        RecoveryControlFixture.write(f.materials.resolve(f.pin + ".json"), f.manifest);

        assertThatThrownBy(f::read).hasMessage("RECOVERY_SOURCE_PIN_MISMATCH");
    }

    @Test void rejectsNoncanonicalPinnedManifest() throws Exception {
        var f = RecoverySourceFixture.create();
        byte[] bytes = f.manifest.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String pin = RecoveryControlFormat.sha256(bytes); Files.write(f.materials.resolve(pin + ".json"), bytes);

        assertThatThrownBy(() -> f.reader().read(f.materials, pin, f.identity)).hasMessage("CONTROL_NONCANONICAL_JSON");
    }

    @Test void doesNotInferTheExternalPinFromMaterialFiles() throws Exception {
        var f = RecoverySourceFixture.create();

        assertThatThrownBy(() -> f.reader().read(f.materials, null, f.identity)).hasMessage("RECOVERY_SOURCE_PIN_REQUIRED");
        assertThatThrownBy(() -> f.reader().read(f.materials, "0".repeat(64), f.identity)).hasMessage("RECOVERY_SOURCE_PIN_REQUIRED");
    }

    @Test void rejectsMaterialWithinAnyRestoreRoot() throws Exception {
        var f = RecoverySourceFixture.create();
        var location = new RecoveryControlLocation(f.control.root, f.control.installation, List.of(f.control.restoreRoot, f.materials));

        assertThatThrownBy(() -> new RecoverySourceReader(location, f.control.checkpoint).read(f.materials, f.pin, f.identity))
            .hasMessage("RECOVERY_SOURCE_INSIDE_RESTORE_SET");
    }

    @Test void rejectsMaterialWithinTheAuthorityOrCheckpoint() throws Exception {
        var f = RecoverySourceFixture.create();

        assertThatThrownBy(() -> f.reader().read(f.control.root, f.pin, f.identity)).hasMessage("RECOVERY_SOURCE_INSIDE_AUTHORITY");
        assertThatThrownBy(() -> f.reader().read(f.control.root.getParent(), f.pin, f.identity))
            .hasMessage("RECOVERY_SOURCE_INSIDE_AUTHORITY");
    }

    @Test void cannotReadWhenActualControlIsQuiescing() throws Exception {
        var f = RecoverySourceFixture.create();
        Files.move(f.control.root.resolve("history/000000000000000002.json"), f.materials.resolve("preserved-control.json"));
        var first = RecoveryControlHistory.read(f.control.root.resolve("history/000000000000000001.json"));
        RecoveryControlFixture.write(f.control.root.resolve("current.json"), RecoveryControlFixture.pointer(first));

        assertThatThrownBy(f::read).hasMessage("RECOVERY_SOURCE_PHASE");
    }

    @Test void staleTrustedControlCannotBeReplacedByTheInventory() throws Exception {
        var f = RecoverySourceFixture.create(); f.read();
        var pointer = RecoveryControlFixture.pointer(f.control.latest).put("recordSha256", "b".repeat(64));
        RecoveryControlFixture.write(f.control.checkpoint, pointer);

        assertThatThrownBy(f::read).hasMessage("CONTROL_CHECKPOINT_MISMATCH");
    }
}
