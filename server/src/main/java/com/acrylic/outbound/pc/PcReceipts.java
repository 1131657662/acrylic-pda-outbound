package com.acrylic.outbound.pc;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.MutationAccess;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Repository
public class PcReceipts {
    private final JdbcTemplate jdbc;private final IdempotencyRequests requests;private final JsonText json;private final AuditWriter audit;
    public PcReceipts(JdbcTemplate jdbc,IdempotencyRequests requests,JsonText json,AuditWriter audit) {
        this.jdbc=jdbc;this.requests=requests;this.json=json;this.audit=audit;
    }
    public Ticket prepare(MutationAccess.Context context,UUID actor,Payload payload) {
        var key=new IdempotencyRequests.Key(actor,"pc."+payload.action(),context.key());
        String digest=json.digest(new Body(context.warehouse(),context.epoch(),payload));UUID previous=requests.find(key,digest);
        var replay=previous==null?null:parse(jdbc.queryForObject("SELECT body_json FROM pc_command_receipt WHERE id=?",String.class,Ids.bytes(previous)));
        return new Ticket(key,digest,payload,replay);
    }
    public JsonNode save(Ticket ticket,MutationAccess.Context context,JsonNode result) {
        UUID id=Ids.next();var body=object().put("receiptId",id.toString()).put("requestKey",context.key())
            .put("orderId",ticket.payload().order().toString()).put("action",ticket.payload().action());body.set("result",result);
        jdbc.update("INSERT INTO pc_command_receipt VALUES(?,?,?,UTC_TIMESTAMP(3))",Ids.bytes(id),Ids.bytes(ticket.payload().order()),canonical(body));
        requests.record(ticket.key(),ticket.digest(),id);
        audit.append(new AuditWriter.Event(ticket.key().actor(),"PC_"+ticket.payload().action(),"ORDER",ticket.payload().order(),canonical(body)),context.requestId());
        return body;
    }
    public void transition(PcStore.Work work,String status,UUID actor,String requestId) {
        jdbc.update("UPDATE outbound_order SET status=?,row_version=row_version+1,updated_at=UTC_TIMESTAMP(3) WHERE id=?",status,Ids.bytes(work.order()));
        long version=jdbc.queryForObject("SELECT row_version FROM outbound_order WHERE id=?",Long.class,Ids.bytes(work.order()));
        var body=object().put("workId",work.id().toString()).put("channel","PC").put("status",status);
        jdbc.update("INSERT INTO order_sync_outbox VALUES(?,?,?,?,?,?,?,NULL,UTC_TIMESTAMP(3))",Ids.bytes(Ids.next()),Long.parseLong(work.epoch()),
            Ids.bytes(work.warehouse()),Ids.bytes(work.order()),version,"PC_"+status,canonical(body));
        audit.append(new AuditWriter.Event(actor,"PC_"+status,"ORDER",work.order(),canonical(body)),requestId);
    }
    public JsonNode lookup(UUID actor,UUID order,String key) {
        if(!key.matches("[A-Za-z0-9._:-]{16,80}")) throw new BusinessException(400,"INVALID_IDEMPOTENCY_KEY","原请求标识无效");
        var rows=jdbc.queryForList("""
            SELECT r.body_json FROM api_idempotency_request i INNER JOIN pc_command_receipt r ON r.id=i.resource_id
            WHERE i.principal_id=? AND i.route_scope LIKE 'pc.%' AND i.idempotency_key=? AND r.order_id=?
            """,String.class,Ids.bytes(actor),key,Ids.bytes(order));
        var result=object();result.set("receipt",rows.isEmpty()?null:parse(rows.getFirst()));return result;
    }
    public record Payload(String action,UUID order,Object command) { }
    private record Body(UUID warehouse,String epoch,Payload payload) { }
    public record Ticket(IdempotencyRequests.Key key,String digest,Payload payload,JsonNode replay) { }
}
