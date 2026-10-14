package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.Ids;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Repository
public class RelayAssociationConflictRefs {
    private final JdbcTemplate jdbc;
    public RelayAssociationConflictRefs(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void insert(RelayAssociationStore.Row row) {
        if(row.request().has("conflictDecisionId"))jdbc.update("INSERT INTO independent_association_conflict VALUES(?,?)",
            Ids.bytes(row.id()),Ids.bytes(uuid(row.request(),"conflictDecisionId")));
    }
    public UUID read(RelayAssociationStore.Row row) {
        var refs=jdbc.queryForList("SELECT BIN_TO_UUID(decision_id) FROM independent_association_conflict WHERE association_id=?",
            String.class,Ids.bytes(row.id()));
        if(!row.request().has("conflictDecisionId")) {
            if(!refs.isEmpty())throw RelayAssociationStore.corrupt();return null;
        }
        if(refs.size()!=1 || !refs.getFirst().equals(text(row.request(),"conflictDecisionId")))throw RelayAssociationStore.corrupt();
        return UUID.fromString(refs.getFirst());
    }
}
