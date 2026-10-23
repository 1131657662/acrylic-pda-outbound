package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Exact external history inside an already acquired closed transaction; never an authority substitute. */
final class RecoveryDatabaseControl {
    private final RecoveryControlLocation location;
    final List<JsonNode> records;

    RecoveryDatabaseControl(RecoveryClosedAuthority authority) {
        this(authority.location(), authority.control());
    }

    private RecoveryDatabaseControl(RecoveryControlLocation location, RecoveryControlSnapshot expected) {
        this.location = location;
        try {
            records = RecoveryControlHistory.history(location);
            RecoverySuccessorEvidence.verify(location.root(), records);
            require(!records.isEmpty() && text(records.getLast(), "recordSha256").equals(expected.recordSha256())
                && revision(records.getLast(), "revision") == Long.parseLong(expected.revision()), "CONTROL_INSTANCE_STALE");
        } catch (IOException error) { throw new RecoveryControlException("CONTROL_AUTHORITY_UNAVAILABLE", error); }
    }

    static RecoveryDatabaseControl readOnly(RecoveryControlLocation location, RecoveryControlSnapshot expected) {
        try {
            return RecoveryControlLock.lockedWaiting(location.root(), 1_000, () -> {
                require(location.checked().equals(location), "CONTROL_AUTHORITY_CHANGED");
                return new RecoveryDatabaseControl(location, expected);
            });
        } catch (IOException error) { throw new RecoveryControlException("CONTROL_AUTHORITY_UNAVAILABLE", error); }
    }

    JsonNode at(long revision) {
        require(revision > 0 && revision <= records.size(), "CONTROL_DATABASE_CHAIN_CONFLICT");
        return records.get(Math.toIntExact(revision - 1));
    }

    JsonNode head() { return records.getLast(); }

    RecoveryNormalIntent.Intent normal(long revision) {
        var record = at(revision);
        require(text(record.path("command"), "phase").equals("RELEASE_READY"), "CONTROL_NORMAL_PHASE_REQUIRED");
        var matches = new ArrayList<JsonNode>();
        try {
            Path first = location.root().resolve(RecoveryNormalIntent.FILE);
            if (Files.exists(first, LinkOption.NOFOLLOW_LINKS)) add(matches, RecoveryControlHistory.read(first), revision);
            Path history = location.root().resolve("normal-history");
            if (Files.exists(history, LinkOption.NOFOLLOW_LINKS)) {
                try (var files = Files.newDirectoryStream(RecoveryControlPaths.checked(history))) {
                    int count = 0;
                    for (Path file : files) {
                        require(++count <= 100000, "CONTROL_SUCCESSOR_NORMAL_LIMIT");
                        add(matches, RecoveryControlHistory.read(file), revision);
                    }
                }
            }
        } catch (IOException error) { throw new RecoveryControlException("CONTROL_NORMAL_INTENT_UNREADABLE", error); }
        require(matches.size() == 1, "CONTROL_SUCCESSOR_NORMAL_REQUIRED");
        return intent(matches.getFirst(), record);
    }

    private static void add(List<JsonNode> matches, JsonNode value, long revision) {
        if (value.path("controlRevision").isIntegralNumber() && value.path("controlRevision").longValue() == revision)
            matches.add(value);
    }

    private static RecoveryNormalIntent.Intent intent(JsonNode value, JsonNode record) {
        fields(value, "formatVersion installationId expectedDatabaseSchema expectedBaselineNumericEpoch controlRevision "
            + "controlRecordSha256 previousRecordSha256 operatorId requestedAt intentSha256"); version(value);
        for (String field : List.of("controlRevision", "expectedBaselineNumericEpoch"))
            require(value.path(field).isIntegralNumber() && value.path(field).canConvertToLong()
                && value.path(field).longValue() > 0, "CONTROL_SUCCESSOR_NORMAL_NUMBER");
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) value.deepCopy(); body.remove("intentSha256");
        require(hash(body).equals(text(value, "intentSha256")), "CONTROL_NORMAL_INTENT_HASH");
        require(uuid(value, "installationId").equals(uuid(record, "installationId"))
            && text(value, "controlRecordSha256").equals(text(record, "recordSha256"))
            && text(value, "previousRecordSha256").equals(text(record, "previousRecordSha256")), "CONTROL_NORMAL_INTENT_CONTROL_MISMATCH");
        instant(value, "requestedAt");
        require(!text(value, "operatorId").isBlank() && !text(value, "expectedDatabaseSchema").isBlank(), "CONTROL_SUCCESSOR_NORMAL_TEXT");
        return new RecoveryNormalIntent.Intent(uuid(value, "installationId"), text(value, "expectedDatabaseSchema"),
            value.path("expectedBaselineNumericEpoch").longValue(), value.path("controlRevision").longValue(),
            text(value, "controlRecordSha256"), text(value, "previousRecordSha256"), text(value, "operatorId"), text(value, "intentSha256"));
    }
}
