package com.acrylic.outbound.relay;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.common.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;

@Component
public class RelayJournal {
    private final IdempotencyRequests requests;private final AuditWriter audit;private final JdbcTemplate jdbc;
    public RelayJournal(IdempotencyRequests requests,AuditWriter audit,JdbcTemplate jdbc) {
        this.requests=requests;this.audit=audit;this.jdbc=jdbc;
    }
    public UUID replay(Command command) {
        try { return requests.find(command.key(),RelayRequestDigest.digest(command.content())); }
        catch(BusinessException error) {
            if(!error.code().equals("IDEMPOTENCY_CONTENT_MISMATCH"))throw error;
            var key=command.key();String old=jdbc.queryForObject("""
                SELECT request_sha256 FROM api_idempotency_request
                WHERE principal_id=? AND route_scope=? AND idempotency_key=?
                """,String.class,Ids.bytes(key.actor()),key.scope(),key.value());
            if(old==null||!RelayRequestDigest.legacyMatches(command,old))throw error;
            return requests.find(key,old);
        }
    }
    public void record(Command command,UUID resource,String requestId) {
        audit.append(new AuditWriter.Event(command.key().actor(),command.key().scope(),"ORIGINAL_RELAY",resource,
            RelayRequestDigest.text(command.content())),requestId);
        requests.record(command.key(),RelayRequestDigest.digest(command.content()),resource);
    }
    public record Command(IdempotencyRequests.Key key,Map<String,String> content) { }
}
