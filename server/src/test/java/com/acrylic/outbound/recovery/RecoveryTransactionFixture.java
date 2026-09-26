package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.*;

/** Uses the independent Python writer, retaining each original and process transcript. */
public final class RecoveryTransactionFixture {
    private static final Path PROJECT = Path.of("D:/acrylic-pda-outbound");
    private static final Path PYTHON = PROJECT.resolve(".local/r09-control-venv/Scripts/python.exe");
    private static final Path TESTS = PROJECT.resolve("docs/parallel/R09/tests");
    final Path root, base, checkpoint;
    final RecoveryControlLocation location;
    private int calls;

    public RecoveryTransactionFixture(Path root) throws Exception {
        this.root = root;
        Files.createDirectories(root.getParent());
        var setup = command(List.of(PYTHON.toString(), TESTS.resolve("control_transaction_fixture.py").toString(), root.toString(),
                root.getParent().resolve("data").toString(), root.getParent().resolve("storage").toString()),
            root.resolveSibling(root.getFileName() + "-setup"));
        assertThat(setup).isEqualTo(new Result(0, "CREATED"));
        var config = RecoveryControlFormat.readCanonical(Files.readAllBytes(root.resolve("fixture.json")));
        base = Path.of(config.path("base").textValue()); checkpoint = Path.of(config.path("checkpoint").textValue());
        var restored = new ArrayList<Path>(); config.path("restoreRoots").forEach(p -> restored.add(Path.of(p.textValue())));
        location = new RecoveryControlLocation(Path.of(config.path("root").textValue()),
            UUID.fromString(config.path("installationId").textValue()), restored);
    }

    RecoveryLeasedTransaction transaction(DataSource source) { return new RecoveryLeasedTransaction(source, location, checkpoint); }

    void assertBlocked() throws Exception {
        byte[] before = Files.readAllBytes(location.root().resolve("current.json"));
        assertThat(advance()).isEqualTo(new Result(2, "CONTROL_WRITERS_ACTIVE"));
        assertThat(Files.readAllBytes(location.root().resolve("current.json"))).isEqualTo(before);
    }

    void assertAdvanced() throws Exception {
        var result = advance(); assertThat(result.code()).isZero();
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.output());
        assertThat(json.path("record").path("revision").asText()).isEqualTo("2");
        assertThat(json.path("ordinaryWritesAllowed").booleanValue()).isFalse();
    }

    void advancePhase(String phase) throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        List<Path> paths;
        try (var entries = Files.list(location.root().resolve("history"))) { paths = entries.sorted().toList(); }
        var previous = mapper.readTree(Files.readAllBytes(paths.get(paths.size() - 1)));
        var command = previous.path("command").deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) command).put("commandId", UUID.randomUUID().toString());
        ((com.fasterxml.jackson.databind.node.ObjectNode) command).put("expectedRevision", previous.path("revision").asText());
        ((com.fasterxml.jackson.databind.node.ObjectNode) command).put("phase", phase);
        if (phase.equals("RECOVERING")) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) command).put("epochId", UUID.randomUUID().toString());
        }
        String request = "phase-" + phase + ".json";
        Files.writeString(base.resolve(request), RecoveryControlFormat.canonical(command), StandardOpenOption.CREATE_NEW);
        var result = command(List.of(PYTHON.toString(), TESTS.resolve("control_process.py").toString(),
            base.toString(), "apply", request), root.resolve("python-phase-" + (++calls)));
        assertThat(result.code()).isZero();
    }

    private Result advance() throws Exception {
        return command(List.of(PYTHON.toString(), TESTS.resolve("control_process.py").toString(),
            base.toString(), "apply", "next.json"), root.resolve("python-call-" + (++calls)));
    }

    private static Result command(List<String> args, Path transcript) throws Exception {
        var out = transcript.resolveSibling(transcript.getFileName() + ".out");
        var err = transcript.resolveSibling(transcript.getFileName() + ".err");
        var process = new ProcessBuilder(args).redirectOutput(out.toFile()).redirectError(err.toFile()).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly(); process.waitFor(10, TimeUnit.SECONDS);
            throw new AssertionError("Control process timed out; originals retained");
        }
        assertThat(Files.readString(err)).isEmpty();
        return new Result(process.exitValue(), Files.readString(out).strip());
    }

    record Result(int code, String output) { }
}
