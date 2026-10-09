package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Full immutable-chain reader; pending histories are never repaired here. */
final class RecoveryControlHistory {
    private RecoveryControlHistory() { }

    static RecoveryControlSnapshot load(RecoveryControlLocation location, Path trusted, RecoveryControlPurpose purpose)
            throws IOException {
        var identity = read(location.root().resolve("identity.json"));
        // The single entry point: the declared purpose decides which authority kind may be loaded.
        purpose.requireKind(identity);
        RecoveryControlRecords.identity(identity, location);
        var records = history(location);
        require(!records.isEmpty(), "CONTROL_NOT_INITIALIZED");
        require(text(identity, "initialCommandSha256").equals(text(records.getFirst(), "commandSha256")), "CONTROL_BOOTSTRAP_MISMATCH");
        RecoveryControlRecords.anchor(read(trusted), records, location.installation());
        int pointer = RecoveryControlRecords.anchor(read(location.root().resolve("current.json")), records, location.installation());
        require(pointer == records.size(), "CONTROL_COMMAND_INCOMPLETE");
        layout(location.root());
        if (Files.exists(location.root().resolve("writers.json")) && read(location.root().resolve("writers.json")).path("formatVersion").asInt() == 2)
            RecoveryControlIntents.validate(location.root(), records);
        return snapshot(records.getLast(), location);
    }

    private static List<JsonNode> history(RecoveryControlLocation location) throws IOException {
        Path directory = RecoveryControlPaths.checked(location.root().resolve("history"));
        require(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS), "CONTROL_HISTORY_MISSING");
        var names = new ArrayList<Path>();
        try (var entries = Files.newDirectoryStream(directory)) {
            for (Path path : entries) {
                require(names.size() < 100000, "CONTROL_HISTORY_LIMIT"); names.add(path);
            }
        }
        names.sort(Comparator.comparing(p -> p.getFileName().toString()));
        var records = new ArrayList<JsonNode>(); var ids = new HashSet<String>();
        for (int i = 0; i < names.size(); i++) {
            require(names.get(i).getFileName().toString().equals(String.format(Locale.ROOT, "%018d.json", i + 1L)), "CONTROL_HISTORY_GAP");
            var value = read(names.get(i));
            RecoveryControlRecords.record(value, records.isEmpty() ? null : records.getLast(), location.installation());
            require(ids.add(text(value.path("command"), "commandId")), "CONTROL_COMMAND_REUSED");
            records.add(value);
        }
        return records;
    }

    private static void layout(Path root) throws IOException {
        var allowed = new HashSet<>(Set.of("identity.json", "coordinator.lock", "history", "current.json"));
        if (RecoveryWriterProtocol.validate(root, false)) {
            allowed.addAll(Set.of("writers.json", "writers.lock"));
            if (read(root.resolve("writers.json")).path("formatVersion").asInt() == 2)
                allowed.addAll(Set.of("intents", "admission.json"));
        }
        // Initial NORMAL admission is a separate durable record; its content is verified by
        // RecoveryNormalIntent, never inferred from its presence here.
        allowed.add(RecoveryNormalIntent.FILE);
        if (Files.exists(root.resolve("closed-sessions"), LinkOption.NOFOLLOW_LINKS)) {
            closedSessions(root.resolve("closed-sessions"));
            allowed.add("closed-sessions");
        }
        try (var entries = Files.newDirectoryStream(root)) {
            for (Path entry : entries) require(allowed.contains(entry.getFileName().toString()), "CONTROL_UNKNOWN_ARTIFACT");
        }
    }

    // Registration is only a consistency witness; independent authority pins are still required.
    private static void closedSessions(Path directory) throws IOException {
        require(Files.isDirectory(RecoveryControlPaths.checked(directory), LinkOption.NOFOLLOW_LINKS),
            "CONTROL_CLOSED_SESSION_LAYOUT");
        int count = 0;
        try (var entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                require(++count <= 100000 && entry.getFileName().toString().matches("[a-f0-9]{64}\\.json"),
                    "CONTROL_CLOSED_SESSION_LAYOUT");
                var value = read(entry);
                fields(value, "formatVersion authorityRoot authoritySha256"); version(value);
                hashField(value, "authoritySha256");
                require(!ZERO_HASH.equals(text(value, "authoritySha256")), "CONTROL_CLOSED_SESSION_LAYOUT");
                require(Path.of(text(value, "authorityRoot")).isAbsolute(), "CONTROL_CLOSED_SESSION_LAYOUT");
            }
        }
    }

    static JsonNode read(Path path) throws IOException {
        Path file = RecoveryControlPaths.file(path);
        try (var stream = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            return readCanonical(stream.readNBytes(RecoveryControlJson.MAX_BYTES + 1));
        }
    }

    private static RecoveryControlSnapshot snapshot(JsonNode record, RecoveryControlLocation location) {
        var command = record.path("command"); var warehouses = new ArrayList<String>();
        command.path("affectedWarehouseIds").forEach(item -> warehouses.add(item.textValue()));
        return new RecoveryControlSnapshot(location.installation(), text(record, "revision"),
            uuid(command, "recoverySessionId"), uuid(command, "epochId"), text(command, "phase"),
            text(command, "snapshotId"), text(command, "manifestSha256"), text(record, "recordSha256"),
            text(record, "previousRecordSha256"),
            List.copyOf(warehouses), text(command, "evidenceSha256"), instant(command, "possibleLossFrom"),
            instant(command, "possibleLossThrough"));
    }
}
