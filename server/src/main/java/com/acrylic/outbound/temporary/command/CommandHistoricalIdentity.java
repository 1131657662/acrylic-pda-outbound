package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reads retained immutable identity facts. Today's roles, versions, expiry and revocation are not history gates. */
@Component
public class CommandHistoricalIdentity {
    private final JdbcTemplate jdbc;
    public CommandHistoricalIdentity(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void verify(JsonNode receipt) {
        var actor=receipt.get("actor");var context=receipt.get("originalContext");
        Instant recorded=Instant.parse(receipt.get("recordedAt").textValue());
        source(context,recorded);
        if(!actor.get("sessionId").isNull())session(actor,context,recorded);
        // Web transport and the full old role/permission set are signed writer assertions; no historical Web session table exists.
    }
    private void session(JsonNode actor,JsonNode context,Instant recorded) {
        var rows=jdbc.queryForList("""
            SELECT s.user_id,s.device_id,s.user_auth_version,s.device_version,s.created_at,s.expires_at,d.warehouse_id
            FROM pda_session s JOIN device d ON d.id=s.device_id WHERE s.id=?
            """,id(actor,"sessionId"));
        CommandErrors.integrity(rows.size()==1);var row=rows.getFirst();
        equal(row,"user_id",actor,"principalId");equal(row,"device_id",actor,"deviceId");equal(row,"warehouse_id",context,"warehouseId");
        CommandErrors.integrity(row.get("user_auth_version").toString().equals(actor.get("userAuthVersion").textValue())
            && row.get("device_version").toString().equals(actor.get("deviceVersion").textValue())
            && !recorded.isBefore(time(row,"created_at")) && recorded.isBefore(time(row,"expires_at")));
    }
    private void source(JsonNode context,Instant recorded) {
        var rows=jdbc.queryForList("""
            SELECT a.*,t.creator_device_id,t.client_order_id FROM temporary_authorization a
            JOIN temporary_work_origin t ON t.creator_device_id=? AND t.client_order_id=?
            WHERE a.id=? AND ((?=1 AND t.authorization_id=a.id) OR EXISTS (
              SELECT 1 FROM temporary_source_continuation_submission r
              JOIN temporary_source_continuation_activation n ON n.ticket_id=r.ticket_id AND n.scope_id=r.scope_id AND n.from_work_round=r.from_work_round
              JOIN temporary_source_continuation q ON q.id=n.ticket_id AND q.previous_authorization_id=t.authorization_id
              JOIN submission_scope c ON c.id=r.scope_id AND c.creator_device_id=t.creator_device_id AND c.client_order_id=t.client_order_id
              JOIN temporary_work_origin fresh ON fresh.creator_device_id=n.target_device_id AND fresh.client_order_id=n.new_client_order_id AND fresh.authorization_id=a.id
              WHERE r.submission_id=? AND r.scope_id=? AND r.to_work_round=? AND r.from_work_round=1
                AND n.new_authorization_id=a.id AND n.target_device_id=a.device_id AND n.target_operator_id=a.user_id))
            """,id(context,"creatorDeviceId"),id(context,"clientOrderId"),id(context,"authorizationId"),context.path("workRound").asLong(),
            id(context,"submissionId"),id(context,"scopeId"),context.path("workRound").asLong());
        CommandErrors.integrity(rows.size()==1);var row=rows.getFirst();
        equal(row,"warehouse_id",context,"warehouseId");equal(row,"device_id",context,"freezeDeviceId");equal(row,"user_id",context,"originalOperatorId");
        var versions=Map.of("authorization_epoch","authorizationEpoch","user_auth_version","authorizationUserVersion",
            "device_version","authorizationDeviceVersion","recovery_epoch","sourceRecoveryEpoch");
        versions.forEach((sql,json)->CommandErrors.integrity(row.get(sql).toString().equals(context.get(json).textValue())));
        CommandErrors.integrity(!recorded.isBefore(time(row,"issued_at")));
    }
    private byte[] id(JsonNode node,String key) { return Ids.bytes(UUID.fromString(node.get(key).textValue())); }
    private void equal(Map<String,Object> row,String column,JsonNode node,String field) {
        CommandErrors.integrity(Arrays.equals((byte[])row.get(column),id(node,field)));
    }
    private Instant time(Map<String,Object> row,String field) { return CommandRecord.instant(row.get(field)); }
}
