package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static com.acrylic.outbound.recovery.RecoveryReleaseRows.*;

/** Inverts only the independently signed actual receiving LOGIN rows, retaining every original comparison. */
final class RecoveryRecipientSessionEffects {
    private final RecoveryRecipientSession session;
    private final JdbcTemplate jdbc;
    RecoveryRecipientSessionEffects(RecoveryRecipientSession session,JdbcTemplate jdbc) { this.session=session; this.jdbc=jdbc; }
    JsonNode before(JsonNode tables) {
        session.materials(); var body=session.facts(); validate(body); ObjectNode result=tables.deepCopy();
        var raw=new RecoveryReleaseDatabaseRows(jdbc).read();
        for(var effect:Map.of("pda_session","session","device_challenge","challenge","operation_event","audit").entrySet()) {
            var expected=RecoveryRecipientSessionFormat.one(body.path("effects").path(effect.getValue()));
            RecoveryRecipientSessionRows.remove((ObjectNode)result.path(effect.getKey()),raw.path(effect.getKey()),effect.getKey(),expected);
        }
        var devices=decode(result.path("device")); String target=id(text(body.path("recipient"),"deviceId"));
        var selected=devices.stream().filter(r->Objects.equals(r.get("id"),target)).toList();
        require(selected.size()==1,"RECOVERY_SESSION_DEVICE_COUNT"); var device=selected.getFirst();
        require(Objects.equals(device.get("last_seen_at"),text(body.path("effects"),"deviceLastSeenAt")),"RECOVERY_SESSION_DEVICE_TIME_CHANGED");
        device.put("last_seen_at",null); write(result,"device",devices); return result;
    }
    private void validate(JsonNode body) {
        var effect=body.path("effects"); var recipient=body.path("recipient"); var login=body.path("login");
        var row=RecoveryRecipientSessionFormat.one(effect.path("session"));
        require(row.keySet().equals(Set.of("id","device_id","user_id","user_auth_version","device_version","access_hash","refresh_hash",
            "access_expires_at","expires_at","revoked_at","created_at")),"RECOVERY_SESSION_COLUMNS");
        RecoveryReleaseRows.uuid(row,"id",text(login,"sessionId"));
        RecoveryReleaseRows.uuid(row,"device_id",text(recipient,"deviceId")); RecoveryReleaseRows.uuid(row,"user_id",text(recipient,"userId"));
        eq(row,"user_auth_version",text(recipient,"userAuthVersion")); eq(row,"device_version",text(recipient,"deviceVersion")); eq(row,"revoked_at",null);
        require(text(row,"access_hash").matches("[a-f0-9]{64}") && text(row,"refresh_hash").matches("[a-f0-9]{64}")
            && !text(row,"access_hash").equals(text(row,"refresh_hash")),"RECOVERY_SESSION_TOKEN_HASH");
        Instant created=time(row,"created_at"),at=instant(body,"requestedAt");
        require(time(row,"access_expires_at").equals(created.plusSeconds(900)) && time(row,"expires_at").equals(created.plusSeconds(30L*86400))
            && !at.isBefore(created) && !instant(body,"expiresAt").isAfter(time(row,"access_expires_at")),"RECOVERY_SESSION_TIME");
        challenge(body,created); audit(body,created);
        Instant now=jdbc.queryForObject("SELECT UTC_TIMESTAMP(3)",java.sql.Timestamp.class).toInstant();
        require(!at.isAfter(now) && instant(body,"expiresAt").isAfter(now),"RECOVERY_SESSION_EXPIRED");
    }
    private void challenge(JsonNode body,Instant created) {
        var row=RecoveryRecipientSessionFormat.one(body.path("effects").path("challenge"));
        require(row.keySet().equals(Set.of("id","device_id","purpose","nonce","expires_at","used_at","created_at")),"RECOVERY_SESSION_CHALLENGE_COLUMNS");
        require(text(row,"nonce").matches("[A-Za-z0-9_-]{43}"),"RECOVERY_SESSION_CHALLENGE_NONCE");
        Instant issued=time(row,"created_at"),used=time(row,"used_at"),expires=time(row,"expires_at");
        require(!used.isBefore(issued) && used.isBefore(expires) && !created.isBefore(used)
            && expires.isAfter(issued) && !expires.isAfter(issued.plusSeconds(121)),"RECOVERY_SESSION_CHALLENGE_TIME");
    }
    private void audit(JsonNode body,Instant created) {
        var row=RecoveryRecipientSessionFormat.one(body.path("effects").path("audit")); var recipient=body.path("recipient");
        require(row.keySet().equals(Set.of("id","actor_id","event_type","entity_type","entity_id","details","server_time","request_id")),"RECOVERY_SESSION_AUDIT_COLUMNS");
        RecoveryReleaseRows.uuid(row,"actor_id",text(recipient,"userId")); RecoveryReleaseRows.uuid(row,"entity_id",text(recipient,"deviceId"));
        eq(row,"event_type","PDA_LOGIN"); eq(row,"entity_type","DEVICE"); eq(row,"request_id",text(body.path("login"),"requestId"));
        require(text(row,"id").matches("[1-9][0-9]*") && com.acrylic.outbound.submissions.SubmissionJson.parse(text(row,"details"))
            .equals(com.acrylic.outbound.submissions.SubmissionJson.object()),"RECOVERY_SESSION_AUDIT_DETAILS");
        Instant at=time(row,"server_time");
        require(!at.isBefore(created) && !at.isAfter(instant(body,"requestedAt")),"RECOVERY_SESSION_AUDIT_TIME");
        Instant seen=time(Map.of("at",text(body.path("effects"),"deviceLastSeenAt")),"at");
        require(!seen.isBefore(created) && !seen.isAfter(at),"RECOVERY_SESSION_DEVICE_TIME");
    }
    private static void write(ObjectNode result,String name,List<Map<String,String>> rows) {
        var table=(ObjectNode)result.path(name); var encoded=encode(table.path("columns"),rows);
        if(table.has("rows")) table.set("rows",encoded); else table.put("rowsSha256",sha256(RecoveryTemporaryEvidenceMaterial.encode(encoded)));
        if(table.has("rowCount")) table.put("rowCount",Integer.toString(rows.size()));
    }
}
