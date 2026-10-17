package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

/** Private configuration and explicit runtime classpath for disposable P5 observer/owner JVMs. */
final class RecoveryRetirementProcess {
    static Path config(Path directory, RecoveryControlFixture control,
            RecoveryInitialAuthorityProvision.Provisioned pin) throws Exception {
        var value = RecoveryControlFixture.JSON.createObjectNode().put("root", control.root.toString())
            .put("installation", control.installation.toString()).put("restore", control.restoreRoot.toString())
            .put("checkpoint", control.checkpoint.toString()).put("lease", directory.resolve("p5").toString())
            .put("closed", directory.resolve("closed").toString()).put("identitySha256", pin.identitySha256())
            .put("markerSha256", pin.markerSha256()).put("anchor", RecoveryControlFormat.hex(pin.anchor()));
        Path path = directory.resolve("config.json");
        Files.writeString(path, RecoveryControlFormat.canonical(value), StandardOpenOption.CREATE_NEW);
        return path;
    }

    static RecoveryFenceAuthority authority(Path config) throws Exception {
        var value = RecoveryControlHistory.read(config);
        var location = location(value);
        var binding = new RecoveryLeaseRoots.Binding(location, Path.of(value.path("lease").asText()),
            Path.of(value.path("closed").asText()), value.path("identitySha256").asText());
        return new RecoveryFenceAuthority(new RecoveryLeaseRoots(binding, value.path("markerSha256").asText(),
            RecoveryControlFormat.bytes(value.path("anchor").asText())));
    }

    static RecoveryControlLocation location(com.fasterxml.jackson.databind.JsonNode value) {
        return new RecoveryControlLocation(Path.of(value.path("root").asText()),
            UUID.fromString(value.path("installation").asText()), List.of(Path.of(value.path("restore").asText())));
    }

    static Process launch(Path config, String mode, Path output) throws Exception {
        var paths = RecoveryControlFixture.JSON.readTree(Files.readAllBytes(Path.of("build/recovery-control-classpath.json")));
        var classpath = new ArrayList<String>(); paths.forEach(p -> classpath.add(p.asText()));
        var command = List.of(Path.of(System.getProperty("java.home"), "bin/java.exe").toString(), "-Xmx256m",
            "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-Dfile.encoding=UTF-8",
            "-Djava.io.tmpdir=" + config.getParent().resolve("tmp"), "-cp", String.join(java.io.File.pathSeparator, classpath),
            RecoveryRetirementChildMain.class.getName(), config.toString(), mode);
        Files.createDirectories(config.getParent().resolve("tmp"));
        Files.writeString(output.resolveSibling(output.getFileName() + ".command.json"), RecoveryControlFixture.JSON.writeValueAsString(command));
        return new ProcessBuilder(command).redirectOutput(output.toFile())
            .redirectError(output.resolveSibling(output.getFileName() + ".stderr").toFile()).start();
    }

    static int finish(Process process) throws Exception {
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly(); assertThat(process.waitFor(15, TimeUnit.SECONDS)).isTrue();
            throw new AssertionError("Disposable retirement JVM timed out");
        }
        return process.exitValue();
    }
}
