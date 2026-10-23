package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;
import com.acrylic.outbound.recovery.RecoveryProjectionRows.*;

/** Retained logical facts consumed by every successor-lineage check, without further SQL. */
record RecoveryLineageFacts(Binding original, List<Projection> projections, List<Epoch> epochs,
        RecoveryNormalRows.Record initialNormal, String originalBindingHash, String originalNormalHash,
        List<ObjectNode> bindings, List<ObjectNode> normals) {
    RecoveryLineageFacts {
        projections = List.copyOf(projections); epochs = List.copyOf(epochs);
        bindings = bindings.stream().map(ObjectNode::deepCopy).toList();
        normals = normals.stream().map(ObjectNode::deepCopy).toList();
    }

    /** Command callers already hold the closed transaction and server_state lock. */
    static RecoveryLineageFacts read(JdbcTemplate jdbc) {
        var rows = new RecoveryProjectionRows(jdbc); var original = rows.binding();
        require(original != null, "CONTROL_DATABASE_NOT_REGISTERED");
        var successors = new RecoverySuccessorRows(jdbc);
        return new RecoveryLineageFacts(original, rows.history(original.installation()), rows.epochs(original.installation()),
            new RecoveryNormalRows(jdbc).existing(), RecoveryDatabaseHashes.binding(jdbc), RecoveryDatabaseHashes.normal(jdbc),
            successors.bindings(original.installation()), successors.normals(original.installation()));
    }
}
