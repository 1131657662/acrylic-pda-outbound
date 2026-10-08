package com.acrylic.outbound.handover;

import java.util.*;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Checks the retained owner proofs against the frozen complete physical checklist. */
@Component
final class TemporaryHandoverOwnerProof {
    private final HandoverStore cases;private final HandoverTemporaryActivationTicketStore tickets;
    TemporaryHandoverOwnerProof(HandoverStore cases,HandoverTemporaryActivationTicketStore tickets) {
        this.cases=cases;this.tickets=tickets;
    }
    List<HandoverTemporaryActivationTicketStore.Owner> read(HandoverTemporaryActivationTicketStore.Ticket ticket) {
        var all=object().putArray("items");var retained=new HashMap<UUID,com.fasterxml.jackson.databind.JsonNode>();
        for(var row:cases.checklist(ticket.caseId())) {
            var line=HandoverTemporaryActivationTicketStore.line(row);all.add(line);
            if("RETAINED".equals(text(line,"state")) && retained.put(uuid(line,"baleId"),line)!=null)
                throw TemporaryHandoverIntakeFacts.changed();
        }
        equal(sha(all),ticket.checklistSha(),"交接完整实物清单摘要不符");
        var owners=tickets.owners(ticket.id());equal(owners.size(),retained.size(),"交接保留证明集合不符");
        for(var owner:owners) {
            var line=retained.get(owner.baleId());if(line==null)throw TemporaryHandoverIntakeFacts.changed();
            equal(number(line,"dispatchCycle"),owner.dispatchCycle(),"交接保留证明周期不符");
            equal(sha(line),owner.recordSha(),"交接逐包保留证明摘要不符");
        }
        return owners;
    }
}
