package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Full immutable-chain reader; pending histories are never repaired here. */
final class RecoveryControlHistory {
    private RecoveryControlHistory() { }

    static RecoveryControlSnapshot load(RecoveryControlLocation location, Path trusted) throws IOException {
        var identity = read(location.root().resolve("identity.json"));
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
        try (var entries = Files.newDirectoryStream(root)) {
            for (Path entry : entries) require(allowed.contains(entry.getFileName().toString()), "CONTROL_UNKNOWN_ARTIFACT");
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
