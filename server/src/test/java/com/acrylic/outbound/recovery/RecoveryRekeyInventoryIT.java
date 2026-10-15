package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Independent Python validates signed membership, not only each surviving record's HMAC. */
class RecoveryRekeyInventoryIT {
    @TempDir(cleanup = CleanupMode.NEVER) Path parent;

    @Test void authenticatesProductSnapshotWithoutAuthorizingCurrentLivenessOrRecovery() throws Exception {
        var f = RecoveryRekeyInspectFixture.rotated(parent);
        var report = f.report("manifest", 0);
        assertThat(report.path("state").asText()).isEqualTo("ALL_NEW");
        assertThat(report.path("processSetAuthenticated").asBoolean()).isTrue();
        var manifest = RecoveryControlHistory.read(f.backup().resolve(RecoveryRekeyInventory.FILE));
        assertThat(manifest.path("recordCount").asInt()).isEqualTo(3);
        anchorTag(f.rotation().supply().roots().anchor(), manifest, "inventoryTag");
    }

    @Test void missingBothOriginalAndCurrentRecordCannotShrinkTheAuthenticatedSet() throws Exception {
        var f = RecoveryRekeyInspectFixture.rotated(parent); Path original = f.processFiles().getFirst();
        Path base = f.rotation().supply().base();
        Files.move(original, base.resolve("retained-missing-old.json"));
        Files.move(f.rotation().supply().roots().root().resolve("processes").resolve(original.getFileName()),
            base.resolve("retained-missing-new.json"));
        assertThat(f.inspect("missing-both", 3)).isEqualTo("UNKNOWN");
    }

    @Test void aValidlySignedManifestCannotBeReboundToAnotherRotation() throws Exception {
        var f = RecoveryRekeyInspectFixture.rotated(parent);
        var manifest = manifest(f); manifest.put("newAnchorId", "f".repeat(64));
        manifest.remove("inventoryTag");
        manifest.put("inventoryTag", hmac(f.rotation().supply().roots().anchor(), manifest));
        replaceManifest(f, manifest);
        assertThat(f.inspect("wrong-binding", 3)).isEqualTo("UNKNOWN");
    }

    @Test void alteredInventoryDigestDoesNotPassWithTheOriginalSignature() throws Exception {
        var f = RecoveryRekeyInspectFixture.rotated(parent);
        var manifest = manifest(f); manifest.put("recordsSha256", "0".repeat(64)); replaceManifest(f, manifest);
        assertThat(f.inspect("tamper", 3)).isEqualTo("UNKNOWN");
    }

    @Test void signedButInconsistentCountStillFailsIndependentRecomputation() throws Exception {
        var f = RecoveryRekeyInspectFixture.rotated(parent); var manifest = manifest(f);
        manifest.put("recordCount", 2); manifest.remove("inventoryTag");
        manifest.put("inventoryTag", hmac(f.rotation().supply().roots().anchor(), manifest)); replaceManifest(f, manifest);
        assertThat(f.inspect("wrong-count", 3)).isEqualTo("UNKNOWN");
    }

    @Test void legacyBackupWithoutManifestStaysExplicitlyUnauthenticated() throws Exception {
        var f = RecoveryRekeyInspectFixture.rotated(parent);
        Files.move(f.backup().resolve(RecoveryRekeyInventory.FILE), f.rotation().supply().base().resolve("retained-manifest.json"));
        var report = f.report("legacy", 0);
        assertThat(report.path("state").asText()).isEqualTo("ALL_NEW");
        assertThat(report.path("processSetAuthenticated").asBoolean()).isFalse();
    }

    @Test void emptyProcessSnapshotIsExplicitRatherThanRequiringInventedHistory() throws Exception {
        var rotation = RecoveryRekeyCliFixture.create(parent); Path process;
        try (var files = Files.list(rotation.supply().roots().root().resolve("processes"))) { process = files.findFirst().orElseThrow(); }
        Files.move(process, rotation.supply().base().resolve("retained-before-snapshot.json"));
        var result = rotation.child(); assertThat(result.status()).isZero();
        var receipt = readCanonical(result.output().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var f = new RecoveryRekeyInspectFixture(rotation, Path.of(text(receipt, "evidenceDirectory")), RecoveryRekeyInspectFixture.keys(rotation));
        var report = f.report("empty-at-snapshot", 0);
        assertThat(report.path("processSetAuthenticated").asBoolean()).isTrue();
        assertThat(report.path("counts").path("new").asInt()).isEqualTo(2);
    }

    private ObjectNode manifest(RecoveryRekeyInspectFixture f) throws Exception {
        return (ObjectNode) RecoveryControlHistory.read(f.backup().resolve(RecoveryRekeyInventory.FILE));
    }

    private void replaceManifest(RecoveryRekeyInspectFixture f, ObjectNode value) throws Exception {
        Path path = f.backup().resolve(RecoveryRekeyInventory.FILE);
        Files.copy(path, f.rotation().supply().base().resolve("retained-original-manifest.json"));
        Files.writeString(path, canonical(value));
    }
}
