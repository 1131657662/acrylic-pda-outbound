package com.acrylic.outbound.temporary;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.PdaSessions;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TemporaryEvents {
    private final IdempotencyRequests requests;private final JsonText json;private final AuditWriter audit;
    public TemporaryEvents(IdempotencyRequests requests,JsonText json,AuditWriter audit) { this.requests=requests;this.json=json;this.audit=audit; }
    public Ticket prepare(PdaSessions.Principal principal,TemporaryCommands.Context context,Command command) {
        var key=new IdempotencyRequests.Key(principal.userId(),"pda.temporary."+command.action(),context.key());
        String digest=json.digest(new Payload(principal.deviceId(),principal.warehouseId(),context.epoch(),command));
        return new Ticket(key,digest,requests.find(key,digest));
    }
    public void save(Ticket ticket,UUID id) { requests.record(ticket.key(),ticket.digest(),id); }
    public void audit(TemporaryStore.Stored row,String event,String requestId) {
        audit.append(new AuditWriter.Event(row.userId(),event,"TEMPORARY_AUTHORIZATION",row.id(),
            json.encode(new Event(row.id(),row.receipt().bundleId(),row.receipt().manifestSha256(),row.authorizationEpoch()))),requestId);
    }
    public String digest(Object body) { return json.digest(body); }
    public record Command(String action,UUID id,Object body) { }
    private record Payload(UUID deviceId,UUID warehouseId,String epoch,Command command) { }
    private record Event(UUID authorizationId,String bundleId,String manifestSha256,String authorizationEpoch) { }
    public record Ticket(IdempotencyRequests.Key key,String digest,UUID replay) { }
}
