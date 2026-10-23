package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static com.acrylic.outbound.recovery.RecoveryAdmissionEvidence.*;

/** Observes only; a valid immutable full lineage is required before any successor can be admitted. */
final class RecoverySuccessorAdmissionQueries {
    private RecoverySuccessorAdmissionQueries() { }

    static Closed read(JdbcTemplate jdbc, Context context) {
        var external = RecoveryDatabaseControl.readOnly(context.location(), context.control());
        String document = jdbc.queryForObject(RecoverySuccessorAdmissionSql.SELECT, String.class);
        try { return verify(new com.fasterxml.jackson.databind.ObjectMapper().readTree(document), external, context.baseline()); }
        catch (java.io.IOException error) { throw new RecoveryControlException("CONTROL_DATABASE_OBSERVATION", error); }
    }

    static Closed verify(JsonNode document, RecoveryDatabaseControl external, long baseline) {
        var facts = RecoveryLineageObservation.facts(document);
        var view = new RecoverySuccessorLineage(facts, external, baseline).read();
        var terminal = view.terminal(); var actual = new Database(uuid(document, "actualServer"), text(document, "actualSchema"));
        view.requireDatabase(new RecoveryProjectionRows.Database(actual.serverUuid().toString(), actual.schema()));
        require(view.binding() != null && view.normal() != null && view.intent() != null
            && terminal.revision() == revision(external.head(), "revision") && terminal.phase().equals("RELEASE_READY"),
            "CONTROL_SUCCESSOR_NORMAL_REQUIRED");
        return new Closed(actual, new Database(UUID.fromString(view.serverUuid()), view.schema()),
            new Binding(facts.original().installation(), facts.original().baseline()),
            new Admission(terminal.revision(), terminal.hash(), view.intent().intentSha256()),
            new Projection(terminal.revision(), terminal.hash(), terminal.phase(), terminal.numeric()),
            new State(text(document, "mode"), RecoveryLineageObservation.number(document, "numeric")));
    }
}
