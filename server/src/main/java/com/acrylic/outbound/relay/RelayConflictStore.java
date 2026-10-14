package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Repository
public class RelayConflictStore {
    private final JdbcTemplate jdbc;
    public RelayConflictStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void available(UUID warehouse,JsonNode claims) {
        for(var claim:claims) {
            int count=jdbc.queryForObject("SELECT COUNT(*) FROM independent_conflict_claim WHERE warehouse_id=? AND bale_id=? AND dispatch_cycle=?",
                Integer.class,Ids.bytes(warehouse),Ids.bytes(uuid(claim,"baleId")),Long.parseLong(text(claim,"dispatchCycle")));
            if(count!=0)throw RelayConflictPolicy.blocked("该包/周期已有决定，请保留原决定并走专门扩展/更正，不得以新键覆盖");
        }
    }
    public void insert(Row row) {
        var s=row.scope();jdbc.update("INSERT INTO independent_conflict_decision VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",Ids.bytes(row.id()),
            Ids.bytes(s.warehouse()),Ids.bytes(s.relay()),Ids.bytes(s.device()),Ids.bytes(s.task()),Ids.bytes(s.actor()),sha(row.request()),sha(row.context()),
            canonical(row.request()),canonical(row.context()),canonical(row.receipt()),Timestamp.from(row.at()));
        for(var physical:checks(row.context()))jdbc.update("INSERT INTO independent_conflict_evidence VALUES(?,?,?)",Ids.bytes(row.id()),
            Ids.bytes(uuid(physical.path("receipt"),"relayId")),Ids.bytes(uuid(physical.path("receipt"),"receiptId")));
        for(var claim:row.context().path("claims"))jdbc.update("INSERT INTO independent_conflict_claim VALUES(?,?,?,?,?)",Ids.bytes(s.warehouse()),
            Ids.bytes(uuid(claim,"baleId")),Long.parseLong(text(claim,"dispatchCycle")),Ids.bytes(row.id()),text(claim,"barcode"));
    }
    public Row read(UUID warehouse,UUID id) {
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(relay_id),BIN_TO_UUID(device_id),BIN_TO_UUID(task_id),BIN_TO_UUID(supervisor_id),
              request_text,context_text,receipt_text,recorded_at,request_sha256,context_sha256
            FROM independent_conflict_decision WHERE id=? AND warehouse_id=?
            """,(rs,n)->{
                var body=parse(rs.getString(5));var context=parse(rs.getString(6));
                if(!sha(body).equals(rs.getString(9)) || !sha(context).equals(rs.getString(10)))throw corrupt();
                return new Row(id,new Scope(warehouse,UUID.fromString(rs.getString(1)),UUID.fromString(rs.getString(2)),
                    UUID.fromString(rs.getString(3)),UUID.fromString(rs.getString(4))),body,context,parse(rs.getString(7)),rs.getTimestamp(8).toInstant());
            },Ids.bytes(id),Ids.bytes(warehouse));
        if(rows.size()!=1)throw missing();return rows.getFirst();
    }
    public void references(Row row) {
        var actual=jdbc.query("SELECT BIN_TO_UUID(relay_id),BIN_TO_UUID(physical_check_id) FROM independent_conflict_evidence WHERE decision_id=?",
            (rs,n)->rs.getString(1)+"/"+rs.getString(2),Ids.bytes(row.id()));
        var expected=checks(row.context()).stream().map(check->text(check.path("receipt"),"relayId")+"/"+text(check.path("receipt"),"receiptId")).toList();
        if(!new TreeSet<>(actual).equals(new TreeSet<>(expected)) || actual.size()!=expected.size())throw corrupt();
        var claims=jdbc.query("SELECT BIN_TO_UUID(bale_id),dispatch_cycle,barcode FROM independent_conflict_claim WHERE decision_id=? AND warehouse_id=?",
            (rs,n)->rs.getString(1)+"/"+rs.getLong(2)+"/"+rs.getString(3),Ids.bytes(row.id()),Ids.bytes(row.scope().warehouse()));
        var selected=new TreeSet<String>();row.context().path("claims").forEach(c->selected.add(text(c,"baleId")+"/"+text(c,"dispatchCycle")+"/"+text(c,"barcode")));
        if(!new TreeSet<>(claims).equals(selected) || claims.size()!=selected.size())throw corrupt();
    }
    static List<JsonNode> checks(JsonNode context) {
        var result=new ArrayList<JsonNode>();result.add(context.path("selected").path("physical"));
        context.path("peers").forEach(row->result.add(row.path("physical")));return List.copyOf(result);
    }
    static BusinessException missing() { return new BusinessException(404,"INDEPENDENT_CONFLICT_NOT_FOUND","冲突决定不存在或无权访问"); }
    static BusinessException corrupt() { return new BusinessException(409,"INDEPENDENT_CONFLICT_INTEGRITY","原冲突决定不可核验，请保全核对"); }
    public record Scope(UUID warehouse,UUID relay,UUID device,UUID task,UUID actor) { }
    public record Row(UUID id,Scope scope,JsonNode request,JsonNode context,JsonNode receipt,Instant at) { }
}
