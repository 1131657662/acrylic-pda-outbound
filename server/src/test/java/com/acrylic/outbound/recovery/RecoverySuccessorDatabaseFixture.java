package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Synthetic Python-written histories; clones preserve originals and explicitly rebind only the test lock inode. */
public final class RecoverySuccessorDatabaseFixture {
    private static final Path PROJECT = Path.of("D:/acrylic-pda-outbound");
    private final Path base;
    private final RecoveryControlLocation location;
    private final Path checkpoint;
    private final long baseline;
    private int commands;

    public RecoverySuccessorDatabaseFixture(JdbcTemplate jdbc, Path home) throws Exception {
        var fixture = new RecoveryTransactionFixture(home);
        base = fixture.base; location = fixture.location; checkpoint = fixture.checkpoint;
        baseline = jdbc.queryForObject("SELECT recovery_epoch FROM server_state WHERE id=1", Long.class);
        project(jdbc);
        for (String phase : List.of("RECOVERING", "VERIFYING", "RELEASE_READY")) {
            fixture.advancePhase(phase); project(jdbc);
        }
        writeNormal(jdbc);
        assertThat(new RecoveryNormalRegistration(jdbc.getDataSource(), location, checkpoint, baseline).register().outcome())
            .isEqualTo("REGISTERED");
    }

    private RecoverySuccessorDatabaseFixture(RecoverySuccessorDatabaseFixture source, Path copy) throws Exception {
        base = Files.createDirectory(copy); baseline = source.baseline;
        Path authority = Files.createDirectory(base.resolve("authority"));
        try (var files = Files.walk(source.location.root())) {
            for (Path file : files.filter(p -> !p.equals(source.location.root())).toList()) {
                Path target = authority.resolve(source.location.root().relativize(file));
                if (Files.isDirectory(file)) Files.createDirectory(target); else Files.copy(file, target);
            }
        }
        location = new RecoveryControlLocation(authority, source.location.installation(), source.location.restoreRoots());
        checkpoint = Files.copy(source.checkpoint, base.resolve("checkpoint.json"));
        var descriptor = (ObjectNode) RecoveryControlHistory.read(authority.resolve("writers.json"));
        descriptor.put("fileIdentity", RecoveryWriterProtocol.fileIdentity(authority.resolve("writers.lock")));
        RecoveryControlFixture.write(authority.resolve("writers.json"), descriptor);
        var process = RecoveryControlFixture.JSON.createObjectNode().put("root", authority.toString())
            .put("installation", location.installation().toString());
        process.set("restore", RecoveryControlFixture.JSON.valueToTree(location.restoreRoots().stream().map(Path::toString).toList()));
        RecoveryControlFixture.write(base.resolve("process.json"), process);
    }

    RecoverySuccessorDatabaseFixture fork(Path copy) throws Exception { return new RecoverySuccessorDatabaseFixture(this, copy); }
    RecoveryClosedAuthority authority() throws Exception { return RecoveryClosedTestSupply.control(location, checkpoint).authority(); }
    ObjectNode head() throws Exception {
        return (ObjectNode) RecoveryControlHistory.read(location.root().resolve("history/"
            + String.format(Locale.ROOT, "%018d.json", Long.parseLong(new RecoveryControlReader(location).read(checkpoint).revision()))));
    }

    RecoveryDatabaseSource.Pins pins(JdbcTemplate jdbc) throws Exception {
        return RecoveryDatabaseSource.inspect(jdbc.getDataSource(), authority(), baseline);
    }

    void successor(JdbcTemplate target, RecoveryDatabaseSource.Pins pins, Map<String, String> changes) throws Exception {
        var previous = head(); var command = (ObjectNode) previous.path("command").deepCopy();
        command.put("formatVersion", 2).put("commandId", UUID.randomUUID().toString())
            .put("expectedRevision", text(previous, "revision")).put("phase", "QUIESCING")
            .put("recoverySessionId", UUID.randomUUID().toString())
            .put("predecessorReleaseSha256", pins.predecessorReleaseSha256())
            .put("predecessorNormalIntentSha256", pins.predecessorNormalIntentSha256())
            .put("sourceBindingSha256", pins.sourceBindingSha256()).put("sourceNormalSha256", pins.sourceNormalSha256())
            .put("targetServerUuid", target.queryForObject("SELECT @@server_uuid", String.class))
            .put("targetDatabaseSchema", target.queryForObject("SELECT DATABASE()", String.class));
        changes.forEach(command::put); apply(command);
    }

    void advance(String phase) throws Exception {
        var previous = head(); var command = (ObjectNode) previous.path("command").deepCopy();
        command.put("commandId", UUID.randomUUID().toString()).put("expectedRevision", text(previous, "revision")).put("phase", phase);
        if (phase.equals("RECOVERING")) command.put("epochId", UUID.randomUUID().toString());
        apply(command);
    }

    private void apply(ObjectNode command) throws Exception {
        String name = "successor-" + (++commands) + ".json";
        Files.writeString(base.resolve(name), canonical(command), StandardOpenOption.CREATE_NEW);
        var args = List.of(PROJECT.resolve(".local/r09-control-venv/Scripts/python.exe").toString(),
            PROJECT.resolve("docs/parallel/R09/tests/control_process.py").toString(), base.toString(), "apply", name);
        Path stdout = base.resolve(name + ".stdout"), stderr = base.resolve(name + ".stderr");
        var process = new ProcessBuilder(args).redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Python command timed out"); }
        assertThat(process.exitValue()).as(Files.readString(stdout) + Files.readString(stderr)).isZero();
    }

    public RecoveryClosedProjection.Result project(JdbcTemplate jdbc) throws Exception {
        return new RecoveryClosedProjection(jdbc.getDataSource(), authority(), baseline).project();
    }

    RecoverySuccessorNormalRegistration.Result admit(JdbcTemplate jdbc) throws Exception {
        return new RecoverySuccessorNormalRegistration(jdbc.getDataSource(), authority(), baseline).register();
    }

    Path writeNormal(JdbcTemplate jdbc) throws Exception {
        var record = head(); var intent = RecoveryControlFixture.JSON.createObjectNode().put("formatVersion", 1)
            .put("installationId", location.installation().toString())
            .put("expectedDatabaseSchema", jdbc.queryForObject("SELECT DATABASE()", String.class))
            .put("expectedBaselineNumericEpoch", baseline).put("controlRevision", revision(record, "revision"))
            .put("controlRecordSha256", text(record, "recordSha256")).put("previousRecordSha256", text(record, "previousRecordSha256"))
            .put("operatorId", "synthetic-successor-operator").put("requestedAt", "2026-10-23T00:00:00Z");
        intent.put("intentSha256", hash(intent));
        Path path = location.root().resolve(RecoveryNormalIntent.FILE);
        if (Files.exists(path)) {
            Files.createDirectories(location.root().resolve("normal-history"));
            path = location.root().resolve("normal-history/" + text(intent, "intentSha256") + ".json");
        }
        Files.writeString(path, canonical(intent), StandardOpenOption.CREATE_NEW); return path;
    }

    boolean runtimeAdmitted(JdbcTemplate jdbc) {
        return RecoveryAdmissionVerifier.of(new RecoveryAdmissionQueries(jdbc.getDataSource()),
            new RecoveryControlReader(location), checkpoint, location, baseline).verify().admitted();
    }

    long baseline() { return baseline; }
    Path root() { return location.root(); }
}
