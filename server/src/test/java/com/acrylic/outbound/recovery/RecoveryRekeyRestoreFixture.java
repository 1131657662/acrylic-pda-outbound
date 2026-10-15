package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

record RecoveryRekeyRestoreFixture(RecoveryRekeyInspectFixture inspection, String inventoryHash) {
    static RecoveryRekeyRestoreFixture rotated(Path parent) throws Exception {
        var f = RecoveryRekeyInspectFixture.rotated(parent);
        return new RecoveryRekeyRestoreFixture(f, hash(RecoveryControlHistory.read(f.backup().resolve(RecoveryRekeyInventory.FILE))));
    }

    RecoveryRekeyCliFixture.Output restore(String pin) throws Exception {
        var f = inspection.rotation();
        var output = new java.io.ByteArrayOutputStream(); var error = new java.io.ByteArrayOutputStream();
        int status = RecoveryRekeyRestoreCli.run(new String[]{"--request", f.request().toString(), "--backup", inspection.backup().toString(),
            "--inventory-sha256", pin}, new RecoveryRekeyCli.Streams(new java.io.ByteArrayInputStream(f.pipe()),
            new java.io.PrintStream(output), new java.io.PrintStream(error)));
        var result = new RecoveryRekeyCliFixture.Output(status, output.toString(java.nio.charset.StandardCharsets.UTF_8),
            error.toString(java.nio.charset.StandardCharsets.UTF_8));
        Path logs = Files.createTempDirectory(f.supply().base(), "restore-local-");
        Files.writeString(logs.resolve("stdout.json"), result.output()); Files.writeString(logs.resolve("stderr.txt"), result.error());
        return result;
    }

    int python(String scenario) throws Exception {
        var f = inspection.rotation(); Path logs = Files.createDirectory(f.supply().base().resolve("restore-python-" + scenario));
        String tool = Path.of(System.getenv("P07_TOOLS_ROOT"), "recovery_rekey_restore.py").toString();
        String java = Path.of(System.getProperty("java.home"), "bin", RecoveryControlPaths.WINDOWS ? "java.exe" : "java").toString();
        var command = List.of(System.getenv("P07_PYTHON"), tool, "--java", java, "--classpath",
            Path.of("build/recovery-rekey-classpath.json").toAbsolutePath().toString(), "--request", f.request().toString(),
            "--backup", inspection.backup().toString(), "--inventory-sha256", inventoryHash,
            "--current-key-file", inspection.keys().resolve("old.bin").toString(),
            "--next-key-file", inspection.keys().resolve("next.bin").toString(), "--evidence-dir", logs.resolve("evidence").toString());
        Files.writeString(logs.resolve("command.txt"), String.join(" ", command));
        var child = new ProcessBuilder(command).redirectOutput(logs.resolve("stdout.json").toFile())
            .redirectError(logs.resolve("stderr.txt").toFile()).start();
        try { assertThat(child.waitFor(40, TimeUnit.SECONDS)).isTrue(); return child.exitValue(); }
        finally { if (child.isAlive()) { child.destroyForcibly(); child.waitFor(5, TimeUnit.SECONDS); } }
    }
}
