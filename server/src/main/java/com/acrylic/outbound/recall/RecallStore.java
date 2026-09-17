package com.acrylic.outbound.recall;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.orders.OrderWriter;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Repository
public class RecallStore {
    private final JdbcTemplate jdbc;private final OrderWriter orders;private final com.acrylic.outbound.continuation.ContinuationStore continuations;
    public RecallStore(JdbcTemplate jdbc,OrderWriter orders,com.acrylic.outbound.continuation.ContinuationStore continuations) { this.jdbc=jdbc;this.orders=orders;this.continuations=continuations; }
    public Head get(UUID id) {
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(id),BIN_TO_UUID(warehouse_id),BIN_TO_UUID(order_id),BIN_TO_UUID(assignment_id),revision,recovery_epoch,
             status,reason,stop_receipt,resolution_receipt FROM order_change_request WHERE id=?
            """,(rs,n)->new Head(UUID.fromString(rs.getString(1)),UUID.fromString(rs.getString(2)),UUID.fromString(rs.getString(3)),
                UUID.fromString(rs.getString(4)),rs.getInt(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9),rs.getString(10)),Ids.bytes(id));
        if(rows.isEmpty()) throw new BusinessException(404,"RECALL_NOT_FOUND","召回不存在或无权访问");return rows.getFirst();
    }
    public UUID forAssignment(UUID id) {
        var rows=jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM order_change_request WHERE assignment_id=?",String.class,Ids.bytes(id));
        return rows.isEmpty()?null:UUID.fromString(rows.getFirst());
    }
    public UUID forOrder(UUID order,UUID warehouse) {
        var rows=jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM order_change_request WHERE warehouse_id=? AND order_id=? ORDER BY id DESC LIMIT 1",String.class,Ids.bytes(warehouse),Ids.bytes(order));
        return rows.isEmpty()?null:UUID.fromString(rows.getFirst());
    }
    public OrderWriter.Head lockOrder(UUID warehouse,UUID order) {
        jdbc.queryForList("SELECT id FROM submission_scope WHERE source_order_id=? FOR UPDATE",Ids.bytes(order));
        return orders.lock(warehouse,order);
    }
    public Head lock(Head head) {
        lockOrder(head.warehouse(),head.order());jdbc.queryForList("SELECT id FROM order_change_request WHERE id=? FOR UPDATE",Ids.bytes(head.id()));return get(head.id());
    }
    public Package recovery(UUID request) {
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(id),payload_sha256,actual_count,weight_g,capture_high_watermark,audit_through_seq,issues_json
            FROM recovery_package WHERE request_id=?
            """,(rs,n)->new Package(UUID.fromString(rs.getString(1)),rs.getString(2),rs.getLong(3),rs.getLong(4),rs.getLong(5),rs.getLong(6),rs.getString(7)),Ids.bytes(request));
        return rows.isEmpty()?null:rows.getFirst();
    }
    public JsonNode view(Head h) {
        var result=object().put("changeRequestId",h.id().toString()).put("orderId",h.order().toString()).put("assignmentId",h.assignment().toString())
            .put("revision",h.revision()).put("serverRecoveryEpoch",h.epoch()).put("status",h.status()).put("reason",h.reason())
            .put("rowVersion",jdbc.queryForObject("SELECT row_version FROM outbound_order WHERE id=?",String.class,Ids.bytes(h.order())));
        result.put("deviceId",jdbc.queryForObject("SELECT BIN_TO_UUID(device_id) FROM order_assignment WHERE id=?",String.class,Ids.bytes(h.assignment())));
        var p=recovery(h.id());if(p!=null) result.set("recovery",packageView(p));else result.putNull("recovery");
        if(h.stop()!=null) result.set("stopProof",parse(h.stop().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        if(h.resolution()!=null) result.set("resolutionProof",parse(h.resolution().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var continuation=continuations.forRecall(h.id());
        if(continuation!=null)result.set("continuation",continuations.view(continuations.get(continuation)));
        var previous=jdbc.queryForList("SELECT BIN_TO_UUID(id) AS id,status,work_round FROM submission WHERE assignment_id=? ORDER BY id DESC LIMIT 1",Ids.bytes(h.assignment()));
        if(!previous.isEmpty())result.set("previousSubmission",object().put("id",(String)previous.getFirst().get("id"))
            .put("status",(String)previous.getFirst().get("status")).put("workRound",previous.getFirst().get("work_round").toString()));
        return result;
    }
    public JsonNode packageView(Package p) {
        var result=object().put("recoveryPackageId",p.id().toString()).put("payloadSha256",p.hash()).put("actualCount",Long.toString(p.count()))
            .put("actualWeightGrams",Long.toString(p.grams())).put("captureHighWatermark",Long.toString(p.captureHigh())).put("auditThroughSeq",Long.toString(p.auditHigh()));
        result.set("issues",parse(p.issues().getBytes(java.nio.charset.StandardCharsets.UTF_8)));return result;
    }
    public record Head(UUID id,UUID warehouse,UUID order,UUID assignment,int revision,String epoch,String status,String reason,String stop,String resolution) {}
    public record Package(UUID id,String hash,long count,long grams,long captureHigh,long auditHigh,String issues) {}
}
