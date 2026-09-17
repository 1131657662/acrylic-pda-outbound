package com.acrylic.outbound.pda;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.common.*;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AssignmentEvents {
    private final IdempotencyRequests requests;private final JsonText json;private final AuditWriter audit;private final JdbcTemplate jdbc;
    public AssignmentEvents(IdempotencyRequests requests,JsonText json,AuditWriter audit,JdbcTemplate jdbc) {
        this.requests=requests;this.json=json;this.audit=audit;this.jdbc=jdbc;
    }
    public Ticket prepare(PdaSessions.Principal principal,Input input,String keyValue) {
        var key=new IdempotencyRequests.Key(principal.userId(),"pda.assign",keyValue);
        String digest=json.digest(new Body(principal.deviceId(),principal.warehouseId(),input));
        return new Ticket(key,digest,requests.find(key,digest));
    }
    public void save(Ticket ticket,AssignmentStore.Work work,String requestId) {
        requests.record(ticket.key(),ticket.digest(),work.id());
        String details=json.encode(new Event(work.id(),work.order(),work.principal().deviceId(),work.areaNote()));
        audit.append(new AuditWriter.Event(work.principal().userId(),"ORDER_ASSIGNED","ORDER",work.order(),details),requestId);
        jdbc.update("""
            INSERT INTO order_sync_outbox(id,recovery_epoch,warehouse_id,order_id,entity_version,event_type,payload,created_at)
            SELECT ?,p.recovery_epoch,o.warehouse_id,o.id,o.row_version,'ORDER_ASSIGNED',?,UTC_TIMESTAMP(3)
            FROM outbound_order o INNER JOIN order_publication p ON p.order_id=o.id AND p.revision=o.current_revision WHERE o.id=?
            """,Ids.bytes(Ids.next()),details,Ids.bytes(work.order()));
    }
    public record Input(UUID order,AssignmentService.Command command) { }
    private record Body(UUID device,UUID warehouse,Input input) { }
    public record Ticket(IdempotencyRequests.Key key,String digest,UUID replay) { }
    private record Event(UUID assignmentId,UUID orderId,UUID deviceId,String reason) { }
}
