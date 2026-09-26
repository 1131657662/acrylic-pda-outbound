package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Closed phase commands and immutable context transitions. */
final class RecoveryControlCommand {
    private static final Map<String, String> NEXT = Map.of(
        "QUIESCING", "RECOVERING", "RECOVERING", "VERIFYING", "VERIFYING", "RELEASE_READY");
    private static final List<String> CONTEXT = List.of("installationId", "recoverySessionId", "snapshotId",
        "manifestSha256", "affectedWarehouseIds", "possibleLossFrom", "possibleLossThrough");
    private RecoveryControlCommand() { }

    static void validate(JsonNode value) {
        fields(value, "formatVersion installationId commandId expectedRevision phase epochId recoverySessionId "
            + "snapshotId manifestSha256 affectedWarehouseIds possibleLossFrom possibleLossThrough evidenceSha256 actorId requestedAt");
        version(value);
        for (String key : List.of("installationId", "commandId", "epochId", "recoverySessionId")) uuid(value, key);
        identifier(value, "snapshotId"); identifier(value, "actorId"); revision(value, "expectedRevision");
        String phase = text(value, "phase");
        require(NEXT.containsKey(phase) || "RELEASE_READY".equals(phase), "CONTROL_PHASE_CLOSED_ONLY");
        for (String key : List.of("manifestSha256", "evidenceSha256")) {
            hashField(value, key); require(!ZERO_HASH.equals(text(value, key)), "CONTROL_EVIDENCE_REQUIRED");
        }
        warehouses(value.path("affectedWarehouseIds"));
        var from = instant(value, "possibleLossFrom"); var through = instant(value, "possibleLossThrough");
        require(!from.isAfter(through) && !through.isAfter(instant(value, "requestedAt")), "CONTROL_LOSS_WINDOW");
    }

    private static void warehouses(JsonNode values) {
        require(values.isArray() && values.size() >= 1 && values.size() <= 1000, "CONTROL_WAREHOUSES");
        String previous = null;
        for (JsonNode value : values) {
            require(value.isTextual() && value.textValue().matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}"), "CONTROL_IDENTIFIER");
            require(previous == null || previous.compareTo(value.textValue()) < 0, "CONTROL_WAREHOUSE_ORDER");
            previous = value.textValue();
        }
    }

    static void transition(JsonNode previous, JsonNode command) {
        long expected = previous == null ? 0 : revision(previous, "revision");
        require(revision(command, "expectedRevision") == expected, "CONTROL_CAS_CONFLICT");
        String phase = text(command, "phase");
        if (previous == null) {
            require("QUIESCING".equals(phase), "CONTROL_INITIAL_PHASE"); return;
        }
        var before = previous.path("command");
        require(phase.equals(NEXT.get(text(before, "phase"))), "CONTROL_TRANSITION");
        for (String key : CONTEXT) require(command.get(key).equals(before.get(key)), "CONTROL_CONTEXT_CHANGED");
        boolean changed = !text(command, "epochId").equals(text(before, "epochId"));
        require(changed == "RECOVERING".equals(phase), "CONTROL_EPOCH_TRANSITION");
    }
}
