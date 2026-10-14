package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.Arrays;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

class RecoveryRekeyCliTest {
    @TempDir(cleanup = CleanupMode.NEVER) Path parent;

    @Test void separateJvmRotatesAndNewRuntimeConsumesPreservedProcessFacts() throws Exception {
        var f = RecoveryRekeyCliFixture.create(parent);
        var before = (ObjectNode) f.onlyProcess(); before.remove("anchorTag");
        Path key = f.supply().base().resolve("retained-anchor.bin"); byte[] retained = Files.readAllBytes(key);
        var result = f.child();
        assertThat(result.status()).isZero(); assertThat(result.error()).doesNotContain("RECOVERY_REKEY_");
        var receipt = readCanonical(result.output().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(receipt.path("runtimeUpdated").asBoolean()).isFalse();
        assertThat(receipt.path("processRecords").asInt()).isEqualTo(1);
        Path evidence = Path.of(text(receipt, "evidenceDirectory"));
        var original = (ObjectNode) RecoveryControlHistory.read(evidence.resolve("lease-root.json"));
        assertThat(original.path("anchorId").asText()).isEqualTo(sha256(retained));
        var after = (ObjectNode) f.onlyProcess(); after.remove("anchorTag"); assertThat(after).isEqualTo(before);
        assertThat(Files.readAllBytes(key)).isEqualTo(retained);
        var request = RecoveryRekeyRequest.read(f.request());
        try (var runtime = RecoveryLeaseLifecycle.open(request.binding(), text(receipt, "markerSha256"), hex(f.next()))) {
            runtime.authority().checkEffect();
        }
        assertThatThrownBy(() -> f.supply().roots().verify()).hasMessage("RECOVERY_LEASE_ROOT_UNBOUND");
        assertThat(result.output()).doesNotContain(hex(retained), hex(f.next()));
    }

    @Test void separateJvmCannotRotateWhileLifecycleWriterIsActive() throws Exception {
        var f = RecoveryRekeyCliFixture.create(parent); var authority = f.supply().authority(); authority.validateStartup();
        try {
            var before = f.facts(); var result = f.child();
            assertThat(result.status()).isEqualTo(2); assertThat(result.error()).contains("RECOVERY_REKEY_ACTIVE_WRITER");
            assertThat(f.facts()).isEqualTo(before);
        } finally { authority.retire(); }
    }

    @Test void retainedLeaseIsNeverDiscardedToPermitRotation() throws Exception {
        var f = RecoveryRekeyCliFixture.create(parent); var authority = f.supply().authority();
        authority.validateStartup(); authority.registerOperation(); authority.retire();
        var before = f.facts(); var result = f.child();
        assertThat(result.status()).isEqualTo(2); assertThat(result.error()).contains("RECOVERY_REKEY_ACTIVE_LEASE");
        assertThat(f.facts()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(ints = {0, 63, 65, 256})
    void secretPipeRequiresExactlyTwoRawAnchors(int length) throws Exception {
        var f = RecoveryRekeyCliFixture.create(parent); var before = f.facts();
        var result = f.run(Arrays.copyOf(f.pipe(), length));
        assertThat(result.status()).isEqualTo(2); assertThat(result.error()).contains("RECOVERY_REKEY_SECRET_INPUT");
        assertThat(f.facts()).isEqualTo(before); assertThat(result.output()).isEmpty();
    }

    @Test void wrongCurrentOrNextSecretIsRejectedBeforeAnyWrite() throws Exception {
        var f = RecoveryRekeyCliFixture.create(parent); var before = f.facts();
        for (int offset : new int[]{0, 32}) {
            byte[] pipe = f.pipe(); pipe[offset] ^= 1;
            var result = f.run(pipe);
            assertThat(result.status()).isEqualTo(2);
            assertThat(result.error()).contains(offset == 0 ? "RECOVERY_REKEY_OLD_ANCHOR" : "RECOVERY_REKEY_NEW_ANCHOR");
            assertThat(f.facts()).isEqualTo(before);
        }
    }

    @Test void unavailableHistoricalKeyCannotBeReplacedByCallerAssertion() throws Exception {
        var f = RecoveryRekeyCliFixture.create(parent);
        Files.write(f.supply().base().resolve("retained-anchor.bin"), new byte[32]);
        var before = f.facts(); var result = f.run(f.pipe());
        assertThat(result.status()).isEqualTo(2); assertThat(result.error()).contains("RECOVERY_REKEY_HISTORY_UNAVAILABLE");
        assertThat(f.facts()).isEqualTo(before);
    }

    @Test void historicalKeyInsideWritableBusinessTreeIsRejected() throws Exception {
        var f = RecoveryRekeyCliFixture.create(parent); Path key = f.supply().base().resolve("business/anchor.bin");
        Files.write(key, f.supply().roots().anchor()); f.change("retainedAnchorFile", key.toString());
        var before = f.facts(); var result = f.run(f.pipe());
        assertThat(result.status()).isEqualTo(2); assertThat(result.error()).contains("RECOVERY_REKEY_EXTERNAL_INPUT_REQUIRED");
        assertThat(f.facts()).isEqualTo(before);
    }

    @Test void staleRequestCannotBeReplayedAfterSuccessfulRotation() throws Exception {
        var f = RecoveryRekeyCliFixture.create(parent); assertThat(f.child().status()).isZero();
        var before = f.facts(); var result = f.child();
        assertThat(result.status()).isEqualTo(2); assertThat(result.error()).contains("RECOVERY_LEASE_ROOT_UNBOUND");
        assertThat(f.facts()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"identitySha256", "markerSha256", "installationId", "closedAuthority"})
    void incorrectPinnedIdentityNeverRotatesTheTree(String field) throws Exception {
        var f = RecoveryRekeyCliFixture.create(parent);
        String replacement = switch (field) {
            case "installationId" -> java.util.UUID.randomUUID().toString();
            case "closedAuthority" -> f.supply().base().resolve("other-closed").toString();
            default -> "0".repeat(64);
        };
        f.change(field, replacement); var before = f.facts(); var result = f.run(f.pipe());
        assertThat(result.status()).isEqualTo(2); assertThat(result.output()).isEmpty();
        assertThat(f.facts()).isEqualTo(before);
    }

    @Test void concurrentOriginalRequestsProduceOnlyOneRotation() throws Exception {
        var f = RecoveryRekeyCliFixture.create(parent);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(f::child); var second = pool.submit(f::child);
            assertThat(new int[]{first.get(30, TimeUnit.SECONDS).status(), second.get(30, TimeUnit.SECONDS).status()})
                .containsExactlyInAnyOrder(0, 2);
        }
        assertThat(f.onlyProcess().path("anchorTag").asText()).isNotBlank();
    }

    @Test void failedLastReplacementKeepsOriginalsAndRestoresTargetBytes() throws Exception {
        var f = RecoveryRekeyCliFixture.create(parent); Path root = f.supply().roots().root();
        Files.writeString(root.resolve("lease-root.json.rekey.pending"), "occupied");
        var before = f.facts(); var result = f.child();
        assertThat(result.status()).isEqualTo(3); assertThat(result.error()).contains("RECOVERY_REKEY_IO_UNCERTAIN");
        assertThat(f.facts()).isEqualTo(before);
        try (var paths = Files.list(root.getParent())) {
            Path backup = paths.filter(p -> p.getFileName().toString().startsWith(".rekey-backup-")).findFirst().orElseThrow();
            assertThat(Files.readString(backup.resolve("lease-root.json"))).isEqualTo(Files.readString(root.resolve("lease-root.json")));
        }
    }
}
