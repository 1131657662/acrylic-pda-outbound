package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import com.acrylic.outbound.recovery.RecoveryProjectionRows.*;

/** Complete retained database lineage, cross-checked with exact external originals before any append. */
final class RecoverySuccessorLineage {
    private final JdbcTemplate jdbc;
    private final RecoveryDatabaseControl control;
    private final long baseline;

    RecoverySuccessorLineage(JdbcTemplate jdbc, RecoveryDatabaseControl control, long baseline) {
        this.jdbc = jdbc; this.control = control; this.baseline = baseline;
    }

    View read() {
        var rows = new RecoveryProjectionRows(jdbc); var original = rows.binding();
        require(original != null, "CONTROL_DATABASE_NOT_REGISTERED");
        require(original.installation().equals(uuid(control.head(), "installationId")) && original.baseline() == baseline,
            "CONTROL_DATABASE_INSTALLATION_MISMATCH");
        var prefix = RecoveryDatabasePrefix.validate(rows, original, control);
        var facts = new RecoverySuccessorRows(jdbc);
        var bindings = facts.bindings(original.installation()); var normals = facts.normals(original.installation());
        require(bindings.size() <= 100000 && normals.size() <= 100000, "CONTROL_DATABASE_CHAIN_CONFLICT");
        var initial = initialNormal(original, prefix);
        var view = new View(prefix.getFirst(), original, null, null, RecoveryDatabaseHashes.binding(jdbc),
            RecoveryDatabaseHashes.normal(jdbc), initial);
        int bindingIndex = 0, normalIndex = 0;
        for (var projection : prefix) {
            if (projection.revision() > 1 && projection.phase().equals("QUIESCING")) {
                require(bindingIndex < bindings.size(), "CONTROL_SUCCESSOR_BINDING_MISSING");
                var binding = bindings.get(bindingIndex++);
                validateBinding(binding, projection.revision(), view);
                view = new View(projection, original, binding, null, text(binding, "bindingHash"), null, null);
            }
            if (view.binding() != null && projection.phase().equals("RELEASE_READY") && normalIndex < normals.size()
                    && normals.get(normalIndex).path("revision").longValue() == projection.revision()) {
                var normal = normals.get(normalIndex++); var intent = control.normal(projection.revision());
                requireNormal(intent, view.schema());
                require(normal.equals(RecoverySuccessorRows.normal(view.binding(), intent, projection.numeric())),
                    "CONTROL_SUCCESSOR_NORMAL_CONFLICT");
                view = new View(projection, original, view.binding(), normal, view.bindingHash(), text(normal, "normalHash"), intent);
            }
            view = new View(projection, original, view.binding(), view.normal(), view.bindingHash(), view.normalHash(), view.intent());
        }
        require(bindingIndex == bindings.size() && normalIndex == normals.size(), "CONTROL_SUCCESSOR_ORPHAN_FACT");
        return view;
    }

    private RecoveryNormalIntent.Intent initialNormal(Binding original, List<Projection> prefix) {
        require(prefix.size() >= 4, "CONTROL_SUCCESSOR_PREDECESSOR_REQUIRED");
        var terminal = prefix.get(3); var normal = new RecoveryNormalRows(jdbc).existing();
        require(normal != null && terminal.phase().equals("RELEASE_READY"), "CONTROL_SUCCESSOR_NORMAL_REQUIRED");
        var intent = control.normal(terminal.revision()); requireNormal(intent, original.schema());
        require(normal.revision() == terminal.revision() && normal.authorityRevision() == terminal.revision()
            && normal.hash().equals(terminal.hash()) && normal.previousHash().equals(terminal.previousHash())
            && normal.numeric() == terminal.numeric() && normal.intentHash().equals(intent.intentSha256())
            && normal.operator().equals(intent.operatorId()), "CONTROL_SUCCESSOR_NORMAL_CONFLICT");
        return intent;
    }

    void validateBinding(ObjectNode binding, long revision, View previous) {
        var record = control.at(revision);
        require(record.path("command").path("formatVersion").intValue() == 2
            && text(record.path("command"), "phase").equals("QUIESCING")
            && previous.terminal().phase().equals("RELEASE_READY") && previous.normalHash() != null
            && previous.intent() != null, "CONTROL_SUCCESSOR_PREDECESSOR_REQUIRED");
        require(binding.equals(RecoverySuccessorRows.binding(record)), "CONTROL_SUCCESSOR_BINDING_CONFLICT");
        require(text(binding, "predecessorRelease").equals(previous.terminal().hash())
            && text(binding, "predecessorIntent").equals(previous.intent().intentSha256()), "CONTROL_SUCCESSOR_PREDECESSOR_CONFLICT");
        require(text(binding, "sourceBinding").equals(previous.bindingHash())
            && text(binding, "sourceNormal").equals(previous.normalHash()), "CONTROL_SUCCESSOR_SOURCE_PIN_MISMATCH");
    }

    void requireNormal(RecoveryNormalIntent.Intent intent, String schema) {
        require(intent.baseline() == baseline, "CONTROL_NORMAL_INTENT_BASELINE_MISMATCH");
        require(intent.schema().equals(schema), "CONTROL_NORMAL_INTENT_SCHEMA_MISMATCH");
        require(intent.operatorId().length() <= 64, "CONTROL_NORMAL_INTENT_OPERATOR");
    }

    record View(Projection terminal, Binding original, ObjectNode binding, ObjectNode normal, String bindingHash,
            String normalHash, RecoveryNormalIntent.Intent intent) {
        String serverUuid() { return binding == null ? original.serverUuid() : text(binding, "serverUuid"); }
        String schema() { return binding == null ? original.schema() : text(binding, "schema"); }
        void requireDatabase(Database actual) {
            require(serverUuid().equals(actual.serverUuid()) && schema().equals(actual.schema()), "CONTROL_DATABASE_INSTANCE_MISMATCH");
        }
    }
}
