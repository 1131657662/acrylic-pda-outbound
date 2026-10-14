package com.acrylic.outbound.relay;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class RelayAssociationConflicts {
    private final RelayConflictAccess access;private final RelayConflicts conflicts;private final RelayAssociationConflictRefs refs;
    public RelayAssociationConflicts(RelayConflictAccess access,RelayConflicts conflicts,RelayAssociationConflictRefs refs) {
        this.access=access;this.conflicts=conflicts;this.refs=refs;
    }
    public JsonNode resolve(UUID warehouse,UUID relay,JsonNode request) {
        if(!request.has("conflictDecisionId"))return null;
        access.authorize(warehouse);return conflicts.current(warehouse,relay,uuid(request,"conflictDecisionId"));
    }
    public void insert(RelayAssociationStore.Row row) { refs.insert(row); }
    public JsonNode read(RelayAssociationStore.Row row) {
        UUID id=refs.read(row);if(id==null)return null;
        var evidence=conflicts.read(row.scope().warehouse(),row.scope().relay(),id);
        if(!evidence.path("originalContext").path("selected").path("context").equals(row.context()))throw RelayAssociationStore.corrupt();
        return evidence;
    }
}
