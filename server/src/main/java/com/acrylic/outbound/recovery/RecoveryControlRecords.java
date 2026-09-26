package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Immutable record and checkpoint verification, independent of filesystem I/O. */
final class RecoveryControlRecords {
    private RecoveryControlRecords() { }

    static void record(JsonNode value, JsonNode previous, UUID installation) {
        fields(value, "formatVersion installationId revision previousRecordSha256 command commandSha256 createdAt recordSha256");
        version(value);
        var command = value.path("command");
        RecoveryControlCommand.validate(command);
        RecoveryControlCommand.transition(previous, command);
        require(installation.equals(uuid(value, "installationId")) && installation.equals(uuid(command, "installationId")),
            "CONTROL_INSTALLATION_MISMATCH");
        long expected = previous == null ? 1 : revision(previous, "revision") + 1;
        require(revision(value, "revision") == expected, "CONTROL_CHAIN_REVISION");
        String prior = previous == null ? ZERO_HASH : text(previous, "recordSha256");
        require(prior.equals(text(value, "previousRecordSha256")), "CONTROL_CHAIN_PREVIOUS");
        require(hash(command).equals(text(value, "commandSha256")), "CONTROL_COMMAND_HASH");
        instant(value, "createdAt");
        ObjectNode body = value.deepCopy(); body.remove("recordSha256");
        require(hash(body).equals(text(value, "recordSha256")), "CONTROL_RECORD_HASH");
    }

    static int anchor(JsonNode value, List<JsonNode> records, UUID installation) {
        fields(value, "formatVersion installationId revision recordSha256"); version(value);
        require(installation.equals(uuid(value, "installationId")), "CONTROL_INSTALLATION_MISMATCH");
        hashField(value, "recordSha256"); long number = revision(value, "revision");
        require(number >= 1 && number <= records.size(), "CONTROL_CHECKPOINT_ROLLBACK");
        require(text(value, "recordSha256").equals(text(records.get((int)number - 1), "recordSha256")),
            "CONTROL_CHECKPOINT_MISMATCH");
        return (int)number;
    }

    static void identity(JsonNode value, RecoveryControlLocation location) {
        fields(value, "formatVersion installationId restoreRoots initialCommandSha256"); version(value);
        require(location.installation().equals(uuid(value, "installationId")), "CONTROL_INSTALLATION_MISMATCH");
        hashField(value, "initialCommandSha256");
        var roots = value.path("restoreRoots");
        require(roots.isArray() && roots.size() == location.restoreRoots().size(), "CONTROL_RESTORE_SET_MISMATCH");
        for (int i = 0; i < roots.size(); i++) require(roots.get(i).isTextual()
            && location.restoreRoots().get(i).toString().equals(roots.get(i).textValue()), "CONTROL_RESTORE_SET_MISMATCH");
    }
}
