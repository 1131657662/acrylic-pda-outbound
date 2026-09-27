package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.handover.HandoverCommandService;
import com.acrylic.outbound.handover.HandoverStore;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * R07-1 FORMAL fixture (direction 2): keep the already published order facts, but give the FORMAL submission its
 * own device, operator, session and assignment, so it never shares device-scoped capture sequences or audit
 * batches with the TEMP submission that the shared fixture posts first. All rows are written directly to the real
 * tables under the real constraints — no PDA claim API involved.
 */
public final class HandoverFormalFixtureChecks {
    private HandoverFormalFixtureChecks() { }

    public static void verify(WebApplicationContext context) throws Exception {
        var f = new TemporaryConfirmationFixture(context,false);
        var template = f.source.f;                       // published order + original claim (we reuse its facts only)
        var jdbc = f.jdbc;
        UUID warehouse = f.source.f.web.identity().warehouse();
        UUID order = UUID.fromString(template.order);

        // --- own operator, device, session and assignment for the FORMAL batch -------------------------------
        UUID operator = Ids.next();
        jdbc.update("INSERT INTO app_user(id,username,display_name,password_hash,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(3))",
            Ids.bytes(operator),"fixture-"+operator,"交接夹具人员","x");
        UUID device = Ids.next();
        String publicKey = Base64.getEncoder().encodeToString(("fixture-"+device).getBytes(StandardCharsets.UTF_8));
        jdbc.update("INSERT INTO device(id,warehouse_id,device_code,name,public_key,key_sha256,status,row_version,created_by,created_at) "
            + "VALUES(?,?,?,?,?,?, 'ACTIVE',1,?,UTC_TIMESTAMP(3))",
            Ids.bytes(device),Ids.bytes(warehouse),"FIX-"+device.toString().substring(0,8),"交接夹具设备",publicKey,
            DeviceCrypto.sha(publicKey),Ids.bytes(f.source.f.web.identity().user().id()));
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(operator),Ids.bytes(warehouse));
        // A PDA session is only accepted for a user that actually holds the PDA role (pda:login / pda:submit).
        jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='PDA_OPERATOR'",Ids.bytes(operator));
        String token = DeviceCrypto.secret();
        jdbc.update("""
            INSERT INTO pda_session(id,device_id,user_id,user_auth_version,device_version,access_hash,refresh_hash,
              access_expires_at,expires_at,created_at)
            VALUES(?,?,?,1,1,?,?,TIMESTAMPADD(DAY,1,UTC_TIMESTAMP(3)),TIMESTAMPADD(DAY,2,UTC_TIMESTAMP(3)),UTC_TIMESTAMP(3))
            """,Ids.bytes(Ids.next()),Ids.bytes(device),Ids.bytes(operator),DeviceCrypto.sha(token),DeviceCrypto.sha(DeviceCrypto.secret()));
        // The order allows exactly one active qualification, so terminate the old claim and open the new epoch.
        var active = jdbc.queryForMap("SELECT BIN_TO_UUID(publication_id) publication_id,BIN_TO_UUID(policy_id) policy_id,"
            + "BIN_TO_UUID(inventory_snapshot_id) snapshot_id,plan_sha256,inventory_sha256,revision,recovery_epoch "
            + "FROM order_assignment WHERE order_id=? AND status IN ('ACTIVE','RECALL_PENDING')",Ids.bytes(order));
        long nextEpoch = jdbc.queryForObject("SELECT assignment_epoch+1 FROM outbound_order WHERE id=?",Long.class,Ids.bytes(order));
        jdbc.update("UPDATE order_assignment SET status='CLOSED' WHERE order_id=? AND status IN ('ACTIVE','RECALL_PENDING')",Ids.bytes(order));
        UUID assignment = Ids.next();
        jdbc.update("""
            INSERT INTO order_assignment(id,warehouse_id,order_id,revision,publication_id,device_id,operator_id,epoch,recovery_epoch,
              inventory_snapshot_id,policy_id,plan_sha256,inventory_sha256,start_order_scan_seq,allocation_version,status,area_note,issued_at,expires_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,1,0,'ACTIVE',?,UTC_TIMESTAMP(3),TIMESTAMPADD(HOUR,48,UTC_TIMESTAMP(3)))
            """,Ids.bytes(assignment),Ids.bytes(warehouse),Ids.bytes(order),(int)active.get("revision"),
            Ids.bytes(UUID.fromString(active.get("publication_id").toString())),Ids.bytes(device),Ids.bytes(operator),nextEpoch,
            Long.parseLong(active.get("recovery_epoch").toString()),Ids.bytes(UUID.fromString(active.get("snapshot_id").toString())),
            Ids.bytes(UUID.fromString(active.get("policy_id").toString())),active.get("plan_sha256"),active.get("inventory_sha256"),"交接夹具");
        jdbc.update("UPDATE outbound_order SET assignment_epoch=?,row_version=row_version+1 WHERE id=?",nextEpoch,Ids.bytes(order));

        // --- the FORMAL batch body, rebound to the new device/assignment and its own submission id -------------
        String submission = UUID.randomUUID().toString();
        var body = (ObjectNode)template.body;
        body.put("submissionId",submission).put("assignmentId",assignment.toString())
            .put("assignmentEpoch",Long.toString(nextEpoch)).put("freezeDeviceId",device.toString());
        var events = body.withArray("events");
        for (JsonNode event : events) {
            ((ObjectNode)event).put("assignmentId",assignment.toString()).put("originDeviceId",device.toString());
        }
        ((ObjectNode)events.get(events.size()-1)).put("detail",submission);
        var submitted = template.pda.data(template.request(body),tokensFor(jdbc,token,device));
        assertThat(submitted).as("FORMAL submit").isNotNull();
        String status = jdbc.queryForObject("SELECT status FROM submission WHERE id=UUID_TO_BIN(?)",String.class,submission);
        assertThat(status).as("FORMAL submission status").isIn("REVIEW_REQUIRED","RECEIVING");
    }

    private static JsonNode tokensFor(org.springframework.jdbc.core.JdbcTemplate jdbc,String token,UUID device) {
        return SubmissionJson.object().put("accessToken",token).put("deviceId",device.toString());
    }
}
