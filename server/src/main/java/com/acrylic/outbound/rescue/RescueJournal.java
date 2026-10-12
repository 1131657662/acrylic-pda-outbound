package com.acrylic.outbound.rescue;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.common.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class RescueJournal {
    private final IdempotencyRequests requests;private final AuditWriter audit;private final JsonText json;
    public RescueJournal(IdempotencyRequests requests,AuditWriter audit,JsonText json) {
        this.requests=requests;this.audit=audit;this.json=json;
    }
    public UUID replay(Command command) { return requests.find(command.key(),json.digest(command.content())); }
    public void record(Command command,UUID resource,String requestId) {
        audit.append(new AuditWriter.Event(command.key().actor(),command.key().scope(),"RESCUE",resource,
            json.encode(Map.of("requestSha256",json.digest(command.content())))),requestId);
        requests.record(command.key(),json.digest(command.content()),resource);
    }
    public record Command(IdempotencyRequests.Key key,Object content) { }
}
