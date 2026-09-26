package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Independent test projections. Never use CommandFacts/Identity/receipt JSON as expected context. */
final class TemporaryDecisionFacts {
    private final JdbcTemplate jdbc;
    TemporaryDecisionFacts(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    ObjectNode before(TemporaryConfirmationFixture fixture,UUID actor) {
        var result=SubmissionJson.object();String id=fixture.source.id();
        var submission=row("SELECT id,scope_id,work_round,status,freeze_device_id,operator_id,source_recovery_epoch,temporary_authorization_id FROM submission WHERE id=UUID_TO_BIN(?)",id);
        result.set("submission",submission);String scope=submission.path("scope_id").asText();
        var s=row("SELECT id,warehouse_id,creator_device_id,client_order_id,canonical_order_id,mapping_version,current_submission_id,work_round FROM submission_scope WHERE id=UUID_TO_BIN(?)",scope);
        result.set("scope",s);String order=s.path("canonical_order_id").asText();
        result.set("authorization",row("SELECT id,authorization_epoch,user_auth_version,device_version FROM temporary_authorization WHERE id=UUID_TO_BIN(?)",submission.path("temporary_authorization_id").asText()));
        result.set("mapping",row("SELECT id,canonical_order_id FROM temporary_source_mapping WHERE scope_id=UUID_TO_BIN(?) AND mapping_version=1",scope));
        result.set("decision",row("SELECT decision_version FROM temporary_decision_state WHERE scope_id=UUID_TO_BIN(?)",scope));
        result.set("slot",row("SELECT slot_version,scope_id,current_submission_id,work_round FROM order_submission_slot WHERE order_id=UUID_TO_BIN(?)",order));
        result.set("order",row("SELECT id,order_no,current_revision FROM outbound_order WHERE id=UUID_TO_BIN(?)",order));
        result.set("payload",row("SELECT payload_sha256,received_at FROM submission_payload WHERE submission_id=UUID_TO_BIN(?)",id));
        var upload=jdbc.queryForList("SELECT payload_sha256 FROM submission_upload WHERE submission_id=UUID_TO_BIN(?)",id);
        result.set("upload",upload.isEmpty()?SubmissionJson.object().putNull("payload_sha256"):convert(upload.getFirst()));
        result.set("actor",actor(actor));result.set("formalOrders",formalOrders());
        result.set("server",convert(jdbc.queryForMap("SELECT recovery_epoch,service_mode FROM server_state WHERE id=1")));return result;
    }

    ObjectNode actor(UUID id) {
        var result=row("SELECT id,auth_version,status FROM app_user WHERE id=UUID_TO_BIN(?)",id.toString());
        jdbc.queryForList("SELECT BIN_TO_UUID(warehouse_id) FROM user_warehouse WHERE user_id=UUID_TO_BIN(?)",String.class,id.toString())
            .forEach(result.putArray("warehouses")::add);
        jdbc.queryForList("SELECT BIN_TO_UUID(role_id) FROM user_role WHERE user_id=UUID_TO_BIN(?)",String.class,id.toString())
            .forEach(result.putArray("roles")::add);
        jdbc.queryForList("SELECT code FROM permission p WHERE EXISTS (SELECT 1 FROM role_permission rp JOIN user_role ur ON ur.role_id=rp.role_id WHERE rp.permission_id=p.id AND ur.user_id=UUID_TO_BIN(?))",String.class,id.toString())
            .forEach(result.putArray("permissions")::add);return result;
    }

    ObjectNode record(String command) {
        return row("""
            SELECT r.id,r.command_sha256,r.original_response_sha256,r.unsigned_receipt_sha256,r.recorded_at,e.id event_id
            FROM temporary_command_receipt r JOIN api_idempotency_request q ON q.id=r.api_request_id
            JOIN temporary_decision_event e ON e.receipt_id=r.id WHERE q.idempotency_key=?
            """,command);
    }

    private com.fasterxml.jackson.databind.node.ArrayNode formalOrders() {
        var result=SubmissionJson.object().putArray("orders");
        for(var value:jdbc.queryForList("""
            SELECT o.id,o.warehouse_id,o.source,o.order_no,o.status,o.current_revision,o.row_version,o.created_at,o.updated_at,
              r.id revision_id,r.vehicle_no,r.container_no,r.inventory_snapshot_id,r.mapping_revision FROM outbound_order o
            JOIN order_revision r ON r.order_id=o.id AND r.revision=o.current_revision
            """)) {
            var order=convert(value);
            jdbc.queryForList("SELECT BIN_TO_UUID(product_id) FROM order_line WHERE revision_id=UUID_TO_BIN(?)",String.class,order.path("revision_id").asText())
                .forEach(order.putArray("products")::add);
            var lines=order.putArray("lines");
            jdbc.queryForList("SELECT delivery_code,product_id,model_snapshot,name_snapshot,planned_count FROM order_line WHERE revision_id=UUID_TO_BIN(?) ORDER BY delivery_code",order.path("revision_id").asText())
                .forEach(line->{var raw=convert(line);lines.add(SubmissionJson.object().put("deliveryCode",raw.path("delivery_code").asText())
                    .put("productId",raw.path("product_id").asText()).put("model",raw.path("model_snapshot").asText())
                    .put("name",raw.path("name_snapshot").asText()).put("plannedCount",raw.path("planned_count").asText()));});
            workFacts(order);result.add(order);
        }
        return result;
    }

    private void workFacts(ObjectNode order) {
        String id=order.path("id").asText();
        order.put("hasAssignment",jdbc.queryForObject("SELECT COUNT(*) FROM order_assignment WHERE order_id=UUID_TO_BIN(?)",Long.class,id)>0);
        order.put("hasPcWork",jdbc.queryForObject("SELECT COUNT(*) FROM pc_work WHERE order_id=UUID_TO_BIN(?)",Long.class,id)>0);
        order.put("hasResult",jdbc.queryForObject("SELECT COUNT(*) FROM order_result_version WHERE order_id=UUID_TO_BIN(?)",Long.class,id)>0);
        order.put("hasSubmission",!jdbc.queryForList("SELECT s.id FROM submission s JOIN submission_scope c ON s.scope_id=c.id WHERE c.source_order_id=UUID_TO_BIN(?) OR c.canonical_order_id=UUID_TO_BIN(?)",id,id).isEmpty());
    }

    private ObjectNode row(String sql,String id) { return convert(jdbc.queryForMap(sql,id)); }
    private ObjectNode convert(Map<String,Object> row) {
        var result=SubmissionJson.object();
        row.forEach((name,value)->{
            if(value==null)result.putNull(name);
            else if(value instanceof byte[] bytes) {
                var buffer=java.nio.ByteBuffer.wrap(bytes);result.put(name,new UUID(buffer.getLong(),buffer.getLong()).toString());
            } else if(value instanceof Timestamp time)result.put(name,time.toInstant().toString());
            else if(value instanceof java.time.LocalDateTime time)result.put(name,time.toInstant(java.time.ZoneOffset.UTC).toString());
            else result.put(name,value.toString());
        });return result;
    }
}
