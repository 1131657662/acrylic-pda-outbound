package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

/** Independent Python control producer bound to this disposable datadir and restored storage. */
public final class RecoveryRestoredStaleAuthority {
    private static final Path PROJECT = Path.of("D:/acrylic-pda-outbound");
    private static final Path PYTHON = PROJECT.resolve(".local/r09-control-venv/Scripts/python.exe");
    private final Path evidence;
    private final JsonNode config;

    public RecoveryRestoredStaleAuthority(Path attempt) throws Exception {
        evidence = Files.createDirectory(attempt.resolve("private/stale-authority"));
        Path root = evidence.resolve("control");
        var created = command("setup", List.of(PYTHON.toString(),
            PROJECT.resolve("docs/parallel/R09/tests/control_transaction_fixture.py").toString(),
            root.toString(), attempt.resolve("data").toString(), attempt.resolve("restored/storage").toString()));
        assertThat(created.strip()).isEqualTo("CREATED");
        config = SubmissionJson.parse(Files.readString(root.resolve("fixture.json")));
    }

    public String[] options() {
        var roots = new ArrayList<String>(); config.path("restoreRoots").forEach(p -> roots.add(p.asText()));
        return new String[]{"--outbound.recovery.admission.enabled=true",
            "--outbound.recovery.root=" + config.path("root").asText(),
            "--outbound.recovery.installation-id=" + config.path("installationId").asText(),
            "--outbound.recovery.restore-roots=" + String.join(",", roots),
            "--outbound.recovery.checkpoint=" + config.path("checkpoint").asText()};
    }

    public void advance() throws Exception {
        String output = command("advance", List.of(PYTHON.toString(),
            PROJECT.resolve("docs/parallel/R09/tests/control_process.py").toString(),
            config.path("base").asText(), "apply", "next.json"));
        var result = SubmissionJson.parse(output);
        assertThat(result.path("record").path("revision").asText()).isEqualTo("2");
        assertThat(result.path("ordinaryWritesAllowed").asBoolean()).isFalse();
    }

    private String command(String label, List<String> args) throws Exception {
        Files.writeString(evidence.resolve(label + "-command.json"),
            new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(args), StandardOpenOption.CREATE_NEW);
        Path output = evidence.resolve(label + ".stdout"), error = evidence.resolve(label + ".stderr");
        var process = new ProcessBuilder(args).redirectOutput(output.toFile()).redirectError(error.toFile()).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly(); process.waitFor(10, TimeUnit.SECONDS);
            throw new AssertionError("Owned Python control producer timed out; originals retained");
        }
        Files.writeString(evidence.resolve(label + "-exit.json"), "{\"exitCode\":" + process.exitValue() + "}",
            StandardOpenOption.CREATE_NEW);
        assertThat(process.exitValue()).isZero(); assertThat(Files.readString(error)).isEmpty();
        return Files.readString(output);
    }
}
