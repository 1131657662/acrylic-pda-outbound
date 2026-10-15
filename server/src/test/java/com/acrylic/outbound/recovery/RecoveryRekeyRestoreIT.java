package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

class RecoveryRekeyRestoreIT {
    @TempDir(cleanup = CleanupMode.NEVER) Path parent;

    @Test void actualPythonRestoresKnownMixedTreeAndConsumesEveryOriginalByte() throws Exception {
        var f = RecoveryRekeyRestoreFixture.rotated(parent); var inspection = f.inspection();
        inspection.original("lease-root.json");
        assertThat(f.python("mixed")).isZero();
        assertThat(inspection.inspect("restored", 0)).isEqualTo("ALL_OLD");
        var report = RecoveryControlHistory.read(inspection.rotation().supply().base().resolve("restore-python-mixed/evidence/verified.json"));
        assertThat(report.path("originalBytesRestored").asBoolean()).isTrue();
        Path evidence = Path.of(text(report, "restoreEvidenceDirectory"));
        assertThat(RecoveryControlHistory.read(evidence.resolve("lease-root.json")).path("anchorId").asText())
            .isEqualTo(inspection.rotation().supply().roots().verify().path("anchorId").asText());
    }

    @Test void completeNewTreeCanReturnToOriginalOnlyWithoutLaterFacts() throws Exception {
        var f = RecoveryRekeyRestoreFixture.rotated(parent);
        assertThat(f.python("new")).isZero();
        assertThat(f.inspection().inspect("restored-new", 0)).isEqualTo("ALL_OLD");
    }

    @Test void samePinnedRestoreIsIdempotentAndDoesNotReplaceOriginalFilesAgain() throws Exception {
        var f = RecoveryRekeyRestoreFixture.rotated(parent); assertThat(f.restore(f.inventoryHash()).status()).isZero();
        var before = f.inspection().facts(); var result = f.restore(f.inventoryHash());
        assertThat(result.status()).isZero(); assertThat(f.inspection().facts()).isEqualTo(before);
        assertThat(readCanonical(result.output().getBytes(java.nio.charset.StandardCharsets.UTF_8)).path("alreadyOriginal").asBoolean()).isTrue();
    }

    @Test void concurrentSamePinnedRestoreSerializesAndPreservesOriginals() throws Exception {
        var f = RecoveryRekeyRestoreFixture.rotated(parent);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> f.restore(f.inventoryHash())); var b = pool.submit(() -> f.restore(f.inventoryHash()));
            assertThat(a.get(20, TimeUnit.SECONDS).status()).isZero(); assertThat(b.get(20, TimeUnit.SECONDS).status()).isZero();
        }
        assertThat(f.inspection().inspect("concurrent", 0)).isEqualTo("ALL_OLD");
    }

    @Test void incorrectIndependentInventoryPinNeverMutatesTheCurrentTree() throws Exception {
        var f = RecoveryRekeyRestoreFixture.rotated(parent); var before = f.inspection().facts();
        assertThat(f.restore("0".repeat(64)).status()).isEqualTo(2); assertThat(f.inspection().facts()).isEqualTo(before);
    }

    @Test void unrecognizedPendingRemainsUntouchedAndCannotBeRestored() throws Exception {
        var f = RecoveryRekeyRestoreFixture.rotated(parent); var inspection = f.inspection();
        Files.write(inspection.rotation().supply().roots().root().resolve("lease-root.json.rekey.pending"), new byte[0]);
        var before = inspection.facts(); assertThat(f.restore(f.inventoryHash()).status()).isEqualTo(2);
        assertThat(inspection.facts()).isEqualTo(before);
    }

    @Test void laterCurrentKeyProcessMakesOriginalSnapshotIneligible() throws Exception {
        var f = RecoveryRekeyRestoreFixture.rotated(parent); var rotation = f.inspection().rotation();
        var marker = RecoveryControlHistory.read(rotation.supply().roots().root().resolve("lease-root.json"));
        var binding = RecoveryRekeyRequest.read(rotation.request()).binding();
        try (var runtime = RecoveryLeaseLifecycle.open(binding, hash(marker), hex(rotation.next()))) {
            runtime.authority().checkEffect();
            var before = f.inspection().facts(); assertThat(f.restore(f.inventoryHash()).status()).isEqualTo(2);
            assertThat(f.inspection().facts()).isEqualTo(before);
        }
    }

    @Test void replacedCoordinatorCannotBeAcceptedUsingOnlySamePathAndBytes() throws Exception {
        var f = RecoveryRekeyRestoreFixture.rotated(parent); Path lock = f.inspection().rotation().supply().roots().authorityRoot().resolve("coordinator.lock");
        Files.move(lock, lock.resolveSibling("retained-original-coordinator")); Files.write(lock, new byte[]{48});
        var before = f.inspection().facts(); assertThat(f.restore(f.inventoryHash()).status()).isEqualTo(2);
        assertThat(f.inspection().facts()).isEqualTo(before);
    }

    @Test void actualChildRefusesALifecycleLockEvenWithNoNewRecord() throws Exception {
        var f = RecoveryRekeyRestoreFixture.rotated(parent); Path process;
        try (var files = Files.list(f.inspection().rotation().supply().roots().root().resolve("processes"))) {
            process = files.findFirst().orElseThrow();
        }
        try (var channel = java.nio.channels.FileChannel.open(process, StandardOpenOption.READ, StandardOpenOption.WRITE);
             var lock = channel.lock(RecoveryLeaseProcess.LOCK_BYTE, 1, false)) {
            var before = f.inspection().facts(); assertThat(f.python("active-record")).isEqualTo(2);
            assertThat(f.inspection().facts()).isEqualTo(before);
        }
    }

    @Test void actualChildNeverClearsAHeldUnknownFence() throws Exception {
        var f = RecoveryRekeyRestoreFixture.rotated(parent);
        Path fence = f.inspection().rotation().supply().roots().root().resolve("fenced.lock");
        try (var channel = java.nio.channels.FileChannel.open(fence, StandardOpenOption.READ, StandardOpenOption.WRITE);
             var lock = channel.lock(0, 1, true)) {
            var before = f.inspection().facts(); assertThat(f.python("fenced")).isEqualTo(2);
            assertThat(f.inspection().facts()).isEqualTo(before);
        }
    }

    @Test void missingInventoryOrBothCopiesNeverCausesPartialRestore() throws Exception {
        var f = RecoveryRekeyRestoreFixture.rotated(parent); var inspection = f.inspection();
        Path original = inspection.processFiles().getFirst(), base = inspection.rotation().supply().base();
        Files.move(original, base.resolve("retained-missing-original.json"));
        Files.move(inspection.rotation().supply().roots().root().resolve("processes").resolve(original.getFileName()),
            base.resolve("retained-missing-current.json"));
        var before = inspection.facts(); assertThat(f.restore(f.inventoryHash()).status()).isEqualTo(2);
        assertThat(inspection.facts()).isEqualTo(before);
        Files.move(inspection.backup().resolve(RecoveryRekeyInventory.FILE), base.resolve("retained-missing-inventory.json"));
        before = inspection.facts(); assertThat(f.restore(f.inventoryHash()).status()).isEqualTo(2);
        assertThat(inspection.facts()).isEqualTo(before);
    }

    @Test void alteredCurrentIndexCannotBeInterpretedAsPermissionToDiscardAnActiveLease() throws Exception {
        var f = RecoveryRekeyRestoreFixture.rotated(parent); Path root = f.inspection().rotation().supply().roots().root();
        Path indexPath = root.resolve("leases/index.json");
        var index = (com.fasterxml.jackson.databind.node.ObjectNode) RecoveryControlHistory.read(indexPath);
        Files.copy(indexPath, f.inspection().rotation().supply().base().resolve("retained-original-empty-index.json"));
        index.withArray("entries").add("unknown-inflight"); index.remove("indexTag");
        index.put("indexTag", hmac(f.inspection().rotation().next(), index)); Files.writeString(indexPath, canonical(index));
        var before = f.inspection().facts(); assertThat(f.restore(f.inventoryHash()).status()).isEqualTo(2);
        assertThat(f.inspection().facts()).isEqualTo(before);
    }

    @Test void failedLastAtomicReplacementPreservesSnapshotAndPendingAndBlocksStartup() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(RecoveryControlPaths.WINDOWS, "Windows native rename refusal");
        var f = RecoveryRekeyRestoreFixture.rotated(parent); Path root = f.inspection().rotation().supply().roots().root();
        Path marker = root.resolve("lease-root.json");
        try (var handle = java.nio.channels.FileChannel.open(marker,
                java.util.Set.of(StandardOpenOption.READ, com.sun.nio.file.ExtendedOpenOption.NOSHARE_DELETE))) {
            var result = f.restore(f.inventoryHash()); assertThat(result.status()).isEqualTo(3);
            assertThat(result.error()).contains("RECOVERY_RESTORE_EVIDENCE_RETAINED");
        }
        assertThat(root.resolve("lease-root.json.restore.pending")).exists();
        assertThat(f.inspection().inspect("restore-tail-failure", 3)).isEqualTo("UNKNOWN");
        assertThatThrownBy(() -> f.inspection().rotation().supply().authority().validateStartup()).isInstanceOf(Exception.class);
        try (var paths = Files.list(root.getParent())) {
            Path snapshot = paths.filter(p -> p.getFileName().toString().startsWith(".rekey-restore-before-")).findFirst().orElseThrow();
            assertThat(RecoveryControlHistory.read(snapshot.resolve("lease-root.json")).path("anchorId").asText())
                .isEqualTo(sha256(f.inspection().rotation().next()));
        }
    }
}
