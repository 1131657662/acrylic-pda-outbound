package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Actual Python supplier -> product CLI JVM -> independent Python original-byte/HMAC consumer. */
class RecoveryRekeyPythonIT {
    @TempDir(cleanup = CleanupMode.NEVER) Path parent;

    @Test void supplierRotatesAndIndependentlyConsumesOriginalAndNewEvidence() throws Exception {
        var f = RecoveryRekeyCliFixture.create(parent); Path keys = keys(f);
        Path evidence = f.supply().base().resolve("python-evidence");
        assertThat(run(f, keys, evidence)).isZero();
        var report = RecoveryControlHistory.read(evidence.resolve("verified.json"));
        assertThat(report.path("rotationVerified").asBoolean()).isTrue();
        assertThat(report.path("originalEvidenceRetained").asBoolean()).isTrue();
        assertThat(report.path("processSetAuthenticated").asBoolean()).isTrue();
        assertThat(report.path("runtimeUpdated").asBoolean()).isFalse();
        assertThat(report.path("productionApproved").asBoolean()).isFalse();
        String files = Files.readString(evidence.resolve("invocation.json")) + Files.readString(evidence.resolve("stdout.json"));
        assertThat(files).doesNotContain(hex(f.next()), f.supply().anchorHex());
    }

    @Test void supplierRefusesASecretInsideTheWritableRecoveryTree() throws Exception {
        var f = RecoveryRekeyCliFixture.create(parent); Path keys = f.supply().roots().root().resolve("keys");
        Files.createDirectory(keys); writeKeys(f, keys); var before = f.facts();
        Path evidence = f.supply().base().resolve("refused-evidence");
        assertThat(run(f, keys, evidence)).isEqualTo(3); assertThat(f.facts()).isEqualTo(before);
        assertThat(evidence.resolve("verified.json")).doesNotExist();
    }

    @Test void successfulReceiptCannotBeDowngradedByRemovingItsInventory() throws Exception {
        var f = RecoveryRekeyCliFixture.create(parent); Path keys = keys(f);
        Path evidence = f.supply().base().resolve("pinned-evidence");
        assertThat(run(f, keys, evidence)).isZero();
        var receipt = RecoveryControlHistory.read(evidence.resolve("stdout.json"));
        Path backup = Path.of(text(receipt, "evidenceDirectory"));
        Files.move(backup.resolve(RecoveryRekeyInventory.FILE), f.supply().base().resolve("retained-inventory.json"));
        String script = "import sys,os; from pathlib import Path; sys.path.insert(0,os.environ['P07_TOOLS_ROOT']); "
            + "from recovery_rekey_verify import read,verify; "
            + "verify(read(sys.argv[1]),read(sys.argv[2]),Path(sys.argv[3]).read_bytes(),Path(sys.argv[4]).read_bytes())";
        var child = new ProcessBuilder(System.getenv("P07_PYTHON"), "-c", script, f.request().toString(),
            evidence.resolve("stdout.json").toString(), keys.resolve("current.bin").toString(), keys.resolve("next.bin").toString())
            .redirectOutput(evidence.resolve("downgrade-stdout.txt").toFile())
            .redirectError(evidence.resolve("downgrade-stderr.txt").toFile()).start();
        try {
            assertThat(child.waitFor(15, TimeUnit.SECONDS)).isTrue(); assertThat(child.exitValue()).isEqualTo(1);
            assertThat(Files.readString(evidence.resolve("downgrade-stderr.txt"))).contains("REKEY_VERIFY_INVENTORY_RECEIPT");
        } finally { if (child.isAlive()) { child.destroyForcibly(); child.waitFor(5, TimeUnit.SECONDS); } }
    }

    @Test void supplierNeverOverwritesAnExistingEvidenceDirectory() throws Exception {
        var f = RecoveryRekeyCliFixture.create(parent); Path keys = keys(f);
        Path evidence = Files.createDirectory(f.supply().base().resolve("occupied-evidence"));
        Files.writeString(evidence.resolve("original.txt"), "retain"); var before = f.facts();
        assertThat(run(f, keys, evidence)).isEqualTo(3); assertThat(f.facts()).isEqualTo(before);
        assertThat(Files.readString(evidence.resolve("original.txt"))).isEqualTo("retain");
    }

    @Test void supplierPropagatesProductRefusalWithoutRetryingOrClaimingSuccess() throws Exception {
        var f = RecoveryRekeyCliFixture.create(parent); Path keys = keys(f);
        var authority = f.supply().authority(); authority.validateStartup();
        try {
            var before = f.facts(); Path evidence = f.supply().base().resolve("writer-evidence");
            assertThat(run(f, keys, evidence)).isEqualTo(2); assertThat(f.facts()).isEqualTo(before);
            assertThat(Files.readString(evidence.resolve("stderr.txt"))).contains("RECOVERY_REKEY_ACTIVE_WRITER");
            assertThat(evidence.resolve("verified.json")).doesNotExist();
            assertThat(Files.readString(evidence.resolve("exit.json"))).contains("\"automaticRetry\":false");
        } finally { authority.retire(); }
    }

    private Path keys(RecoveryRekeyCliFixture f) throws Exception {
        Path keys = Files.createDirectory(f.supply().base().resolve("external-secrets")); writeKeys(f, keys); return keys;
    }

    private void writeKeys(RecoveryRekeyCliFixture f, Path keys) throws Exception {
        Files.write(keys.resolve("current.bin"), f.supply().roots().anchor()); Files.write(keys.resolve("next.bin"), f.next());
    }

    private int run(RecoveryRekeyCliFixture f, Path keys, Path evidence) throws Exception {
        String python = Objects.requireNonNull(System.getenv("P07_PYTHON"), "Explicit Python runtime required");
        Path tool = Path.of(Objects.requireNonNull(System.getenv("P07_TOOLS_ROOT")), "recovery_rekey.py");
        Path classpath = Path.of("build/recovery-rekey-classpath.json").toAbsolutePath();
        String binary = RecoveryControlPaths.WINDOWS ? "java.exe" : "java";
        var command = List.of(python, tool.toString(), "--java", Path.of(System.getProperty("java.home"), "bin", binary).toString(),
            "--classpath", classpath.toString(), "--request", f.request().toString(), "--current-key-file",
            keys.resolve("current.bin").toString(), "--next-key-file", keys.resolve("next.bin").toString(),
            "--evidence-dir", evidence.toString());
        Path output = Files.createTempDirectory(f.supply().base(), "supplier-");
        Files.writeString(output.resolve("command.txt"), String.join(" ", command));
        var process = new ProcessBuilder(command).redirectOutput(output.resolve("stdout.txt").toFile())
            .redirectError(output.resolve("stderr.txt").toFile()).start();
        try {
            assertThat(process.waitFor(40, TimeUnit.SECONDS)).as(output.toString()).isTrue(); return process.exitValue();
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
        }
    }
}
