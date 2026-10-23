package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Original NORMAL intents bind successor rounds; external DB pins remain unverified here. */
final class RecoverySuccessorEvidence {
    private static final String NORMAL_FIELDS = "formatVersion installationId expectedDatabaseSchema expectedBaselineNumericEpoch "
        + "controlRevision controlRecordSha256 previousRecordSha256 operatorId requestedAt intentSha256";
    private RecoverySuccessorEvidence() { }

    static void verify(Path root, List<JsonNode> records) throws IOException {
        var values = originals(root, records, records.stream().anyMatch(r -> r.path("command").path("formatVersion").intValue() == 2));
        var rounds = new RecoverySuccessorControl.Rounds();
        for (var record : records) {
            rounds.accept(record.path("command"));
            reference(record.path("command"), values);
        }
    }

    static void pending(Path root, List<JsonNode> records, JsonNode command) throws IOException {
        var rounds = new RecoverySuccessorControl.Rounds();
        for (var record : records) {
            require(!text(record.path("command"), "commandId").equals(text(command, "commandId")), "CONTROL_COMMAND_REUSED");
            rounds.accept(record.path("command"));
        }
        rounds.accept(command);
        reference(command, originals(root, records, command.path("formatVersion").intValue() == 2
            || records.stream().anyMatch(r -> r.path("command").path("formatVersion").intValue() == 2)));
    }

    private static Map<String, JsonNode> originals(Path root, List<JsonNode> records, boolean includeInitial) throws IOException {
        var paths = new ArrayList<Path>();
        Path initial = root.resolve(RecoveryNormalIntent.FILE);
        // Legacy v1 readers leave the optional initial intent to its existing NORMAL consumer.
        if (includeInitial && Files.exists(initial, LinkOption.NOFOLLOW_LINKS)) paths.add(initial);
        Path directory = root.resolve("normal-history");
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            require(Files.isDirectory(RecoveryControlPaths.checked(directory), LinkOption.NOFOLLOW_LINKS),
                "CONTROL_SUCCESSOR_NORMAL_LAYOUT");
            try (var stream = Files.newDirectoryStream(directory)) {
                int count = 0;
                for (var path : stream) {
                    require(++count <= 100000, "CONTROL_SUCCESSOR_NORMAL_LIMIT"); paths.add(path);
                }
            }
        }
        var values = new HashMap<String, JsonNode>();
        for (var path : paths) {
            var value = normal(RecoveryControlHistory.read(path), records);
            String digest = text(value, "intentSha256");
            require(path.equals(initial) || path.getFileName().toString().equals(digest + ".json"), "CONTROL_SUCCESSOR_NORMAL_LAYOUT");
            require(values.putIfAbsent(digest, value) == null, "CONTROL_SUCCESSOR_NORMAL_DUPLICATE");
        }
        return values;
    }

    private static JsonNode normal(JsonNode value, List<JsonNode> records) {
        fields(value, NORMAL_FIELDS); version(value); uuid(value, "installationId");
        for (String key : List.of("expectedBaselineNumericEpoch", "controlRevision"))
            require(value.path(key).isIntegralNumber() && value.path(key).canConvertToLong()
                && value.path(key).longValue() > 0, "CONTROL_SUCCESSOR_NORMAL_NUMBER");
        for (String key : List.of("expectedDatabaseSchema", "operatorId"))
            require(!text(value, key).isBlank(), "CONTROL_SUCCESSOR_NORMAL_TEXT");
        for (String key : List.of("controlRecordSha256", "previousRecordSha256", "intentSha256")) hashField(value, key);
        instant(value, "requestedAt");
        ObjectNode body = value.deepCopy(); body.remove("intentSha256");
        require(hash(body).equals(text(value, "intentSha256")), "CONTROL_NORMAL_INTENT_HASH");
        long revision = value.path("controlRevision").longValue();
        require(revision <= records.size(), "CONTROL_SUCCESSOR_NORMAL_BINDING");
        var record = records.get((int) revision - 1);
        require(text(record.path("command"), "phase").equals("RELEASE_READY")
            && text(value, "installationId").equals(text(record, "installationId"))
            && text(value, "controlRecordSha256").equals(text(record, "recordSha256"))
            && text(value, "previousRecordSha256").equals(text(record, "previousRecordSha256")), "CONTROL_SUCCESSOR_NORMAL_BINDING");
        if (record.path("command").path("formatVersion").intValue() == 2)
            require(text(value, "expectedDatabaseSchema").equals(text(record.path("command"), "targetDatabaseSchema")),
                "CONTROL_SUCCESSOR_NORMAL_BINDING");
        return value;
    }

    private static void reference(JsonNode command, Map<String, JsonNode> values) {
        if (command.path("formatVersion").intValue() != 2 || !text(command, "phase").equals("QUIESCING")) return;
        var value = values.get(text(command, "predecessorNormalIntentSha256"));
        require(value != null, "CONTROL_SUCCESSOR_NORMAL_REQUIRED");
        require(text(value, "controlRecordSha256").equals(text(command, "predecessorReleaseSha256")),
            "CONTROL_SUCCESSOR_NORMAL_BINDING");
    }
}
