package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Validates retained close intents; reading a valid pending intent never admits another writer. */
final class RecoveryControlIntents {
    static final String HEAD_FIELDS = "formatVersion installationId targetRevision intentSha256";

    static void validate(Path root, List<JsonNode> records) throws IOException {
        var marker = RecoveryControlHistory.read(root.resolve("admission.json"));
        var identity = RecoveryControlHistory.read(root.resolve("identity.json"));
        var paths = paths(root); JsonNode last = null;
        require(paths.size() >= Math.max(0, records.size() - 1), "CONTROL_INTENT_GAP");
        for (int index = 0; index < paths.size(); index++) {
            int target = index + 2;
            require(target <= records.size() + 1 && paths.get(index).getFileName().toString().equals(
                String.format(Locale.ROOT, "%018d.json", target)), "CONTROL_INTENT_GAP");
            last = RecoveryControlHistory.read(paths.get(index));
            intent(last, records.get(target - 2));
            if (target <= records.size()) require(last.path("command").equals(records.get(target - 1).path("command")),
                "CONTROL_INTENT_RECORD_CONFLICT");
        }
        header(marker, identity);
        if (last == null) require(revision(marker, "targetRevision") == 1
            && text(marker, "intentSha256").equals(ZERO_HASH), "CONTROL_INTENT_HEAD_CONFLICT");
        else for (String key : HEAD_FIELDS.split(" "))
            require(marker.path(key).equals(last.path(key)), "CONTROL_INTENT_INCOMPLETE");
    }

    static void requireAdmission(Path root) throws IOException {
        var marker = RecoveryControlHistory.read(root.resolve("admission.json"));
        header(marker, RecoveryControlHistory.read(root.resolve("identity.json")));
        var pointer = RecoveryControlHistory.read(root.resolve("current.json"));
        require(revision(marker, "targetRevision") <= revision(pointer, "revision"), "CONTROL_CLOSING_INTENT");
        long target = revision(marker, "targetRevision");
        require(paths(root).size() == target - 1, "CONTROL_INTENT_INCOMPLETE");
        if (target > 1) {
            var intent = RecoveryControlHistory.read(root.resolve("intents").resolve(String.format(Locale.ROOT, "%018d.json", target)));
            require(text(marker, "intentSha256").equals(text(intent, "intentSha256")), "CONTROL_INTENT_HEAD_CONFLICT");
        }
    }

    private static void header(JsonNode value, JsonNode identity) {
        fields(value, HEAD_FIELDS); version(value); hashField(value, "intentSha256");
        require(uuid(value, "installationId").equals(uuid(identity, "installationId")), "CONTROL_INSTALLATION_MISMATCH");
        require(revision(value, "targetRevision") >= 1, "CONTROL_INTENT_HEAD_CONFLICT");
    }

    private static List<Path> paths(Path root) throws IOException {
        var directory = RecoveryControlPaths.checked(root.resolve("intents"));
        require(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS), "CONTROL_INTENTS_MISSING");
        var paths = new ArrayList<Path>();
        try (var stream = Files.newDirectoryStream(directory)) {
            for (var path : stream) {
                require(paths.size() < 100000, "CONTROL_INTENT_LIMIT"); paths.add(path);
            }
        }
        paths.sort(Comparator.comparing(p -> p.getFileName().toString())); return paths;
    }

    private static void intent(JsonNode value, JsonNode previous) {
        fields(value, "formatVersion installationId targetRevision previousRecordSha256 command commandSha256 intentSha256"); version(value);
        var command = value.path("command");
        RecoveryControlCommand.validate(command); RecoveryControlCommand.transition(previous, command);
        require(uuid(value, "installationId").equals(uuid(previous, "installationId"))
            && uuid(command, "installationId").equals(uuid(previous, "installationId")), "CONTROL_INSTALLATION_MISMATCH");
        require(revision(value, "targetRevision") == revision(previous, "revision") + 1
            && text(value, "previousRecordSha256").equals(text(previous, "recordSha256"))
            && text(value, "commandSha256").equals(hash(command)), "CONTROL_INTENT_CORRUPT");
        ObjectNode body = value.deepCopy(); body.remove("intentSha256");
        require(text(value, "intentSha256").equals(hash(body)), "CONTROL_INTENT_CORRUPT");
    }
}
