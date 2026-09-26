package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/**
 * Optional durable external intent for the initial NORMAL admission. It is a separate artifact and
 * not a control-phase command: RELEASE_READY stays the closed-phase terminal record. The hash chain
 * binds it to the control record it derives from, so a forged or replayed intent cannot be used.
 */
final class RecoveryNormalIntent {
    static final String FILE = "normal-admission.json";

    private RecoveryNormalIntent() { }

    static Requirement load(RecoveryControlLocation location, Path root, long baseline) throws IOException {
        Path directory = RecoveryControlPaths.checked(root);
        Path path = directory.resolve(FILE);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return new Requirement(null);
        JsonNode value = read(RecoveryControlPaths.file(path));
        fields(value, "formatVersion installationId expectedDatabaseSchema expectedBaselineNumericEpoch "
            + "controlRevision controlRecordSha256 previousRecordSha256 operatorId requestedAt intentSha256");
        require(value.path("formatVersion").asInt() == 1, "CONTROL_NORMAL_INTENT_FORMAT");
        require(location.installation().equals(uuid(value, "installationId")), "CONTROL_NORMAL_INTENT_INSTALLATION");
        require(nonBlank(value, "expectedDatabaseSchema"), "CONTROL_NORMAL_INTENT_SCHEMA");
        require(value.path("expectedBaselineNumericEpoch").canConvertToLong()
            && value.path("expectedBaselineNumericEpoch").asLong() > 0, "CONTROL_NORMAL_INTENT_BASELINE");
        require(value.path("controlRevision").canConvertToLong()
            && value.path("controlRevision").asLong() > 0, "CONTROL_NORMAL_INTENT_REVISION");
        for (String field : new String[]{"controlRecordSha256", "previousRecordSha256", "intentSha256"})
            hashField(value, field);
        require(nonBlank(value, "operatorId"), "CONTROL_NORMAL_INTENT_OPERATOR");
        instant(value, "requestedAt");
        ObjectNode body = value.deepCopy(); body.remove("intentSha256");
        require(hash(body).equals(text(value, "intentSha256")), "CONTROL_NORMAL_INTENT_HASH");
        return new Requirement(new Intent(location.installation(),
            text(value, "expectedDatabaseSchema"), value.path("expectedBaselineNumericEpoch").asLong(),
            value.path("controlRevision").asLong(), text(value, "controlRecordSha256"),
            text(value, "previousRecordSha256"), text(value, "operatorId"), text(value, "intentSha256")));
    }

    /** The declared intent must bind the same closed-chain head as the durable control record. */
    static void requireBound(Intent intent, RecoveryControlSnapshot control) {
        require(intent.revision() == Long.parseLong(control.revision())
            && intent.recordSha256().equals(control.recordSha256())
            && intent.previousRecordSha256().equals(control.previousRecordSha256()),
            "CONTROL_NORMAL_INTENT_CONTROL_MISMATCH");
    }

    private static JsonNode read(Path path) throws IOException {
        try (var stream = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            return readCanonical(stream.readNBytes(RecoveryControlJson.MAX_BYTES + 1));
        }
    }

    private static boolean nonBlank(JsonNode value, String field) {
        JsonNode node = value.path(field);
        return node.isTextual() && !node.textValue().isBlank();
    }

    record Intent(UUID installation, String schema, long baseline, long revision, String recordSha256,
        String previousRecordSha256, String operatorId, String intentSha256) { }

    record Requirement(Intent intent) { }
}
