package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Explicit command v2 round transitions; no database or NORMAL admission authority. */
final class RecoverySuccessorControl {
    static final String FIELDS = "predecessorReleaseSha256 predecessorNormalIntentSha256 sourceBindingSha256 "
        + "sourceNormalSha256 targetServerUuid targetDatabaseSchema";
    private RecoverySuccessorControl() { }

    static void validate(JsonNode value) {
        for (String key : List.of("predecessorReleaseSha256", "predecessorNormalIntentSha256",
                "sourceBindingSha256", "sourceNormalSha256")) {
            hashField(value, key);
            require(!ZERO_HASH.equals(text(value, key)), "CONTROL_SUCCESSOR_EVIDENCE_REQUIRED");
        }
        uuid(value, "targetServerUuid");
        require(text(value, "targetDatabaseSchema").matches("[A-Za-z0-9_]{1,64}"), "CONTROL_SUCCESSOR_SCHEMA");
    }

    static boolean transition(JsonNode previous, JsonNode command) {
        var before = previous.path("command");
        if (text(before, "phase").equals("RELEASE_READY")) {
            require(command.path("formatVersion").intValue() == 2 && text(command, "phase").equals("QUIESCING"),
                "CONTROL_SUCCESSOR_TRANSITION");
            require(uuid(command, "installationId").equals(uuid(before, "installationId")), "CONTROL_INSTALLATION_MISMATCH");
            require(!uuid(command, "recoverySessionId").equals(uuid(before, "recoverySessionId")), "CONTROL_SESSION_REUSED");
            require(uuid(command, "epochId").equals(uuid(before, "epochId")), "CONTROL_EPOCH_TRANSITION");
            require(text(command, "predecessorReleaseSha256").equals(text(previous, "recordSha256")),
                "CONTROL_SUCCESSOR_PREDECESSOR");
            return true;
        }
        require(command.path("formatVersion").equals(before.path("formatVersion")), "CONTROL_ROUND_VERSION");
        if (command.path("formatVersion").intValue() == 2)
            for (String key : FIELDS.split(" "))
                require(command.path(key).equals(before.path(key)), "CONTROL_SUCCESSOR_CONTEXT_CHANGED");
        return false;
    }

    static final class Rounds {
        private final Set<String> sessions = new HashSet<>();
        private final Set<String> epochs = new HashSet<>();

        void accept(JsonNode command) {
            String phase = text(command, "phase");
            if (phase.equals("QUIESCING"))
                require(sessions.add(text(command, "recoverySessionId")), "CONTROL_SESSION_REUSED");
            if (epochs.isEmpty() || phase.equals("RECOVERING"))
                require(epochs.add(text(command, "epochId")), "CONTROL_EPOCH_REUSED");
        }
    }
}
