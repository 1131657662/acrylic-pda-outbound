package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Synthetic originals retained per case; independent producer exercised separately. */
final class RecoveryControlFixture {
    static final ObjectMapper JSON = new ObjectMapper();
    final Path root, restoreRoot, checkpoint;
    final UUID installation = UUID.randomUUID();
    final ObjectNode command;
    ObjectNode latest;

    private RecoveryControlFixture(Path base) throws IOException {
        this(base, base.resolve("business"));
    }

    private RecoveryControlFixture(Path base, Path restored) throws IOException {
        this(base,restored,null,null);
    }

    private RecoveryControlFixture(Path base,Path restored,java.time.Instant from,java.time.Instant through) throws IOException {
        root = base.resolve("authority"); restoreRoot = restored; checkpoint = base.resolve("trusted.json");
        Files.createDirectories(root.resolve("history")); Files.createDirectories(restoreRoot);
        command = command();
        if(from!=null) command.put("possibleLossFrom",from.toString()).put("possibleLossThrough",through.toString())
            .put("requestedAt",through.plusSeconds(1).toString());
        var identity = JSON.createObjectNode().put("formatVersion", 1).put("installationId", installation.toString());
        identity.putArray("restoreRoots").add(restoreRoot.toRealPath().toString());
        identity.put("initialCommandSha256", RecoveryControlFormat.hash(command));
        write(root.resolve("identity.json"), identity);
        Files.write(root.resolve("coordinator.lock"), new byte[]{48});
        append(command);
        write(checkpoint, pointer(latest));
    }

    static RecoveryControlFixture create() throws IOException {
        Path retained = Path.of("../.local/control-java-cases").toAbsolutePath().normalize();
        Files.createDirectories(retained);
        return new RecoveryControlFixture(Files.createTempDirectory(retained, "case-"));
    }

    static RecoveryControlFixture retainedAt(Path base, Path restored) throws IOException {
        return new RecoveryControlFixture(base, restored.toRealPath());
    }

    static RecoveryControlFixture forRestore(Path restored) throws IOException {
        Path retained = Path.of("../.local/control-java-cases").toAbsolutePath().normalize();
        Files.createDirectories(retained);
        return new RecoveryControlFixture(Files.createTempDirectory(retained, "closed-mysql-"), restored.toRealPath());
    }

    static RecoveryControlFixture forRestore(Path restored,java.time.Instant from,java.time.Instant through) throws IOException {
        Path retained=Path.of("../.local/control-java-cases").toAbsolutePath().normalize(); Files.createDirectories(retained);
        return new RecoveryControlFixture(Files.createTempDirectory(retained,"closed-mysql-"),restored.toRealPath(),from,through);
    }

    private ObjectNode command() {
        var value = JSON.createObjectNode().put("formatVersion", 1).put("installationId", installation.toString())
            .put("commandId", UUID.randomUUID().toString()).put("expectedRevision", "0").put("phase", "QUIESCING")
            .put("epochId", UUID.randomUUID().toString()).put("recoverySessionId", UUID.randomUUID().toString())
            .put("snapshotId", "snapshot-1").put("manifestSha256", "a".repeat(64))
            .put("possibleLossFrom", "2026-09-20T00:00:00Z").put("possibleLossThrough", "2026-09-21T00:00:00Z")
            .put("evidenceSha256", "b".repeat(64)).put("actorId", "operator-1").put("requestedAt", "2026-09-22T00:00:00Z");
        value.putArray("affectedWarehouseIds").add("W1");
        return value;
    }

    ObjectNode next(String phase) {
        ObjectNode value = latest.path("command").deepCopy();
        value.put("commandId", UUID.randomUUID().toString()).put("expectedRevision", latest.path("revision").asText()).put("phase", phase);
        if (phase.equals("RECOVERING")) value.put("epochId", UUID.randomUUID().toString());
        return value;
    }

    void append(ObjectNode value) throws IOException {
        long number = latest == null ? 1 : Long.parseLong(latest.path("revision").asText()) + 1;
        if (Files.isDirectory(root.resolve("intents"))) intend(value);
        var row = JSON.createObjectNode().put("formatVersion", 1).put("installationId", installation.toString())
            .put("revision", Long.toString(number))
            .put("previousRecordSha256", latest == null ? RecoveryControlFormat.ZERO_HASH : latest.path("recordSha256").asText())
            .put("commandSha256", RecoveryControlFormat.hash(value))
            .put("createdAt",java.time.Instant.parse(value.path("requestedAt").asText()).plusSeconds(1).toString());
        row.set("command", value.deepCopy()); row.put("recordSha256", RecoveryControlFormat.hash(row));
        write(root.resolve("history").resolve(String.format(Locale.ROOT, "%018d.json", number)), row);
        write(root.resolve("current.json"), pointer(row)); latest = row;
    }

    void intend(ObjectNode value) throws IOException {
        long number = Long.parseLong(latest.path("revision").asText()) + 1;
        var intent = JSON.createObjectNode().put("formatVersion", 1).put("installationId", installation.toString())
            .put("targetRevision", Long.toString(number)).put("previousRecordSha256", latest.path("recordSha256").asText())
            .put("commandSha256", RecoveryControlFormat.hash(value));
        intent.set("command", value.deepCopy()); intent.put("intentSha256", RecoveryControlFormat.hash(intent));
        write(root.resolve("intents").resolve(String.format(Locale.ROOT, "%018d.json", number)), intent);
        var header = JSON.createObjectNode();
        for (String key : RecoveryControlIntents.HEAD_FIELDS.split(" ")) header.set(key, intent.path(key));
        write(root.resolve("admission.json"), header);
    }

    static ObjectNode pointer(JsonNode row) {
        return JSON.createObjectNode().put("formatVersion", 1).put("installationId", row.path("installationId").asText())
            .put("revision", row.path("revision").asText()).put("recordSha256", row.path("recordSha256").asText());
    }

    static void write(Path path, JsonNode value) throws IOException { Files.writeString(path, RecoveryControlFormat.canonical(value)); }
    RecoveryControlReader reader() { return new RecoveryControlReader(new RecoveryControlLocation(root, installation, List.of(restoreRoot))); }
    RecoveryControlSnapshot read() { return reader().read(checkpoint); }
    RecoveryControlLocation location() { return new RecoveryControlLocation(root, installation, List.of(restoreRoot)); }

    /** Advances the closed-phase chain to RELEASE_READY, the only phase that may precede NORMAL. */
    void toReleaseReady() throws IOException {
        append(next("RECOVERING"));
        append(next("VERIFYING"));
        append(next("RELEASE_READY"));
    }

    static Normal normal(RecoveryControlFixture fixture, String schema) {
        return new Normal(fixture, schema);
    }

    /** Builder for the separate durable NORMAL intent; the caller may tamper before {@link #write()}. */
    static final class Normal {
        private final RecoveryControlFixture fixture;
        private final ObjectNode value;

        private Normal(RecoveryControlFixture fixture, String schema) {
            this.fixture = fixture;
            value = JSON.createObjectNode().put("formatVersion", 1).put("installationId", fixture.installation.toString())
                .put("expectedDatabaseSchema", schema).put("expectedBaselineNumericEpoch", 1)
                .put("controlRevision", fixture.latest.path("revision").asLong())
                .put("controlRecordSha256", fixture.latest.path("recordSha256").asText())
                .put("previousRecordSha256", fixture.latest.path("previousRecordSha256").asText())
                .put("operatorId", "operator-1").put("requestedAt", "2026-09-26T00:00:00Z");
        }

        ObjectNode body() { return value; }

        /** Writes the durable intent with the digest computed over the current body. */
        void write() throws IOException { write(RecoveryControlFormat.hash(value)); }

        /** Writes the intent with an explicit digest, so hash-binding counter-examples can be produced. */
        void write(String declaredHash) throws IOException {
            var copy = value.deepCopy();
            copy.put("intentSha256", declaredHash);
            RecoveryControlFixture.write(fixture.root.resolve(RecoveryNormalIntent.FILE), copy);
        }
    }
}
