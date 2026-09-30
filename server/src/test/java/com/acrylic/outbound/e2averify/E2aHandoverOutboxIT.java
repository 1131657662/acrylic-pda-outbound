package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.auth.SessionUser;
import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.handover.HandoverCommandService;
import com.acrylic.outbound.handover.HandoverStore;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;

/**
 * R07-1 A2.3: the handover outbox. Activation is the chain's order-state change, so the ACTIVATE command must record
 * one outbound notification <b>on its own transaction</b>: either the qualification change and the notification both
 * land, or neither does, and a read-only decision (REQUEST/APPROVE/PREPARE) must record none.
 *
 * <p>The chain is driven through the real command surface end to end (REQUEST→APPROVE→PREPARE→ACTIVATE) on an
 * isolated non-empty MySQL. Verification level is the <b>service layer</b>; the HTTP layer, the PDA device half and
 * the outbox reader/worker are not exercised here.
 *
 * <p>Known limit, recorded rather than papered over: an explicit <i>rollback after the outbox INSERT</i> cannot be
 * demonstrated through this path. Every refusal guard runs <b>before</b> the insert, and the only later step,
 * {@code advanceCase}, is blocked from failing by {@code handover_case_no_reopen}; forcing a failure there would mean
 * dropping a database guard, which this harness will not do. Same-transaction failure/rollback is therefore covered
 * by construction (one transaction, insert after the state change) and by the refusal cases, not by a torn-write run.
 */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aHandoverOutboxIT {
    @Test void activationRecordsExactlyOneOutboundNotificationAndReadOnlyStepsRecordNone() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "42"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        profile.migrate(jdbc, "44");
        try (var context = E2aCrossSourceHttpIT.start(profile, "--outbound.handover.commands.enabled=true")) {
            verify(context, profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "outbox: %s\n".formatted("ACTIVATE records one HANDOVER_ACTIVATED notification; read-only steps record none"));
    }

    private void verify(org.springframework.context.ApplicationContext context, java.nio.file.Path attempt) throws Exception {
        var jdbc = context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        var store = context.getBean(HandoverStore.class);
        var service = context.getBean(HandoverCommandService.class);
        var notes = new StringBuilder("verification level=service layer end-to-end REQUEST->APPROVE->PREPARE->ACTIVATE; HTTP/worker not verified\n");

        var host = jdbc.queryForMap("""
            SELECT BIN_TO_UUID(a.id) assignment_id,BIN_TO_UUID(a.order_id) order_id,BIN_TO_UUID(a.warehouse_id) warehouse_id,
              BIN_TO_UUID(a.device_id) device_id,BIN_TO_UUID(a.operator_id) operator_id,CAST(a.recovery_epoch AS CHAR) recovery_epoch
            FROM order_assignment a WHERE a.status='ACTIVE' LIMIT 1
            """);
        UUID warehouse = UUID.fromString(host.get("warehouse_id").toString());
        UUID order = UUID.fromString(host.get("order_id").toString());
        UUID assignment = UUID.fromString(host.get("assignment_id").toString());
        UUID device = UUID.fromString(host.get("device_id").toString());

        String requesterName = "obx-req-"+Ids.next(); UUID requester = user(jdbc, warehouse, "order:resume", requesterName);
        String approverName = "obx-app-"+Ids.next(); UUID approver = user(jdbc, warehouse, "order:handover", approverName);

        UUID scope = UUID.randomUUID(); UUID submission = UUID.randomUUID();
        jdbc.update("INSERT INTO submission_scope(id,warehouse_id,source_order_id,work_round,current_submission_id) VALUES(?,?,?,1,NULL)",
            Ids.bytes(scope),Ids.bytes(warehouse),Ids.bytes(order));
        jdbc.update("INSERT INTO submission(id,scope_id,assignment_id,freeze_device_id,operator_id,work_round,"
            + "source_recovery_epoch,status,issues_json,receipt_text,created_at,updated_at) "
            + "VALUES(?,?,?,?,?,1,?,'REGISTERED',NULL,NULL,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
            Ids.bytes(submission),Ids.bytes(scope),Ids.bytes(assignment),Ids.bytes(device),
            Ids.bytes(UUID.fromString(host.get("operator_id").toString())),Long.parseLong(host.get("recovery_epoch").toString()));
        jdbc.update("UPDATE submission_scope SET current_submission_id=? WHERE id=?",Ids.bytes(submission),Ids.bytes(scope));

        long outboxBefore = outboxCount(jdbc,order);

        // REQUEST
        var scopeFacts = store.scopeFacts(scope);
        var request = SubmissionJson.object().put("commandVersion",1).put("commandType","REQUEST")
            .put("commandId",UUID.randomUUID().toString()).put("transportRecoveryEpoch","1").put("reason","出站通知验收")
            .put("warehouseId",warehouse.toString()).put("scopeId",scope.toString());
        var requestExpected = SubmissionJson.object();
        for (String key : List.of("scopeId","canonicalOrderId","mappingVersion","fromWorkRound")) requestExpected.set(key,scopeFacts.get(key));
        requestExpected.put("userAuthVersion","1");
        request.set("expected",requestExpected);
        JsonNode receipt = call(service,request,requester,requesterName);
        UUID caseId = UUID.fromString(receipt.path("businessDecision").path("caseId").asText());
        notes.append("REQUEST -> case=").append(caseId).append(" outboxDelta=").append(outboxCount(jdbc,order)-outboxBefore).append('\n');
        assertThat(outboxCount(jdbc,order)).as("REQUEST records no outbound notification").isEqualTo(outboxBefore);

        // APPROVE (needs the device stop proof the release guard demands; that guard's own refusal is covered elsewhere)
        jdbc.update("INSERT INTO handover_evidence(id,case_id,kind,device_id,payload_sha256,high_watermark,receipt_bytes,recorded_at) "
            + "VALUES(?,?,'STOP_PROOF',?,?,7,?,UTC_TIMESTAMP(3))",
            Ids.bytes(Ids.next()),Ids.bytes(caseId),Ids.bytes(device),
            java.util.HexFormat.of().parseHex("c".repeat(64)),"stop-proof".getBytes(StandardCharsets.UTF_8));
        jdbc.update("UPDATE handover_case SET stop_state='DEVICE_ACKED' WHERE id=?",Ids.bytes(caseId));
        var approved = call(service,caseCommand(store,caseId,warehouse,"APPROVE","RETAINED"),approver,approverName);
        assertThat(approved.path("businessDecision").path("status").asText()).isEqualTo("APPROVED");
        notes.append("APPROVE -> status=APPROVED outboxDelta=").append(outboxCount(jdbc,order)-outboxBefore).append('\n');
        assertThat(outboxCount(jdbc,order)).as("APPROVE records no outbound notification").isEqualTo(outboxBefore);

        // PREPARE
        var prepared = call(service,prepare(store,caseId,warehouse,device,
            UUID.fromString(host.get("operator_id").toString())),approver,approverName);
        assertThat(prepared.path("businessDecision").path("status").asText()).isEqualTo("TARGET_PREPARED");
        notes.append("PREPARE -> status=TARGET_PREPARED outboxDelta=").append(outboxCount(jdbc,order)-outboxBefore).append('\n');
        assertThat(outboxCount(jdbc,order)).as("PREPARE records no outbound notification").isEqualTo(outboxBefore);

        // ACTIVATE. Two fixture facts have to hold first, both measured on the isolated instance rather than assumed:
        //  1) the activation guard compares the published revision's snapshot/mapping revision with the warehouse's
        //     baseline (the same guard a normal claim applies). This harness builds the warehouse row and publishes the
        //     revision separately, leaving mapping_revision=0 against the revision's 1 — snapshot ids already agree — so
        //     the counter is aligned to the published revision, which is the state the import path produces.
        //  2) activation advances the order's single work round, which needs the order_submission_slot row; the fixture
        //     publishes the order without one (the table is empty in this harness), so the slot is written here.
        jdbc.update("UPDATE warehouse SET mapping_revision=(SELECT r.mapping_revision FROM order_revision r "
            + "JOIN order_publication p ON p.order_id=r.order_id AND p.revision=r.revision "
            + "WHERE r.order_id=? ORDER BY r.revision DESC LIMIT 1) WHERE id=?",Ids.bytes(order),Ids.bytes(warehouse));
        jdbc.update("INSERT INTO order_submission_slot(order_id,scope_id,work_round,current_submission_id,phase,slot_version) "
            + "VALUES(?,?,1,?,'FROZEN',1)",Ids.bytes(order),Ids.bytes(scope),Ids.bytes(submission));
        var activated = call(service,activate(store,caseId,warehouse,device,
            UUID.fromString(host.get("operator_id").toString())),approver,approverName);
        assertThat(activated.path("businessDecision").path("status").asText()).isEqualTo("ACTIVATED");
        long after = outboxCount(jdbc,order);
        notes.append("ACTIVATE -> status=ACTIVATED outboxDelta=").append(after-outboxBefore).append('\n');
        assertThat(after).as("ACTIVATE records exactly one outbound notification").isEqualTo(outboxBefore+1);
        var row = jdbc.queryForMap("SELECT event_type,entity_version,published_seq,recovery_epoch,"
            + "JSON_UNQUOTE(JSON_EXTRACT(payload,'$.caseId')) case_id,"
            + "JSON_UNQUOTE(JSON_EXTRACT(payload,'$.workRound')) work_round,"
            + "JSON_UNQUOTE(JSON_EXTRACT(payload,'$.targetDeviceId')) target_device "
            + "FROM order_sync_outbox WHERE order_id=? ORDER BY entity_version DESC LIMIT 1",Ids.bytes(order));
        notes.append("outbox -> event=").append(row.get("event_type")).append(" version=").append(row.get("entity_version"))
            .append(" published_seq=").append(row.get("published_seq")).append(" case=").append(row.get("case_id")).append('\n');
        assertThat(row.get("event_type")).as("event type").isEqualTo("HANDOVER_ACTIVATED");
        assertThat(row.get("published_seq")).as("a fresh notification is still pending (recoverable)").isNull();
        assertThat(row.get("case_id")).as("payload carries the case").isEqualTo(caseId.toString());
        assertThat(String.valueOf(row.get("work_round"))).as("payload carries the new work round").isEqualTo("2");
        assertThat(row.get("target_device")).as("payload carries the target device").isEqualTo(device.toString());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_sync_outbox WHERE recovery_epoch=? AND order_id=? "
            + "AND entity_version=?",Integer.class,row.get("recovery_epoch"),Ids.bytes(order),row.get("entity_version")))
            .as("the version space admits exactly one row per (epoch, order, version)").isEqualTo(1);

        // The refusal path of the same command writes nothing: a second ACTIVATE on an already-ACTIVATED case is
        // refused by the state guard, and the outbox must not grow.
        String refused;
        try { call(service,activate(store,caseId,warehouse,device,
            UUID.fromString(host.get("operator_id").toString())),approver,approverName); refused="NO_ERROR"; }
        catch (BusinessException error) { refused=error.code(); }
        notes.append("ACTIVATE again -> ").append(refused).append(" outboxDelta=").append(outboxCount(jdbc,order)-outboxBefore).append('\n');
        assertThat(refused).as("a second activation is refused").isEqualTo("HANDOVER_STATE_INVALID");
        assertThat(outboxCount(jdbc,order)).as("a refused activation records no notification").isEqualTo(after);

        Files.writeString(attempt.resolve("outbox.txt"),notes.toString());
    }

    private static long outboxCount(org.springframework.jdbc.core.JdbcTemplate jdbc, UUID order) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM order_sync_outbox WHERE order_id=?",Long.class,Ids.bytes(order));
    }

    private ObjectNode caseCommand(HandoverStore store, UUID caseId, UUID warehouse, String kind, String checklistState) {
        var facts = store.caseFacts(caseId);
        var command = SubmissionJson.object().put("commandVersion",1).put("commandType",kind)
            .put("commandId",UUID.randomUUID().toString()).put("transportRecoveryEpoch","1").put("reason","出站通知验收")
            .put("warehouseId",warehouse.toString()).put("caseId",caseId.toString());
        if (kind.equals("APPROVE")) {
            command.put("stopState","DEVICE_ACKED").put("integrityState","COMPLETE").put("originalResultState","ABORTED")
                .put("physicalState","MATCHED").put("inputHighWatermark","7");
            command.putArray("checklist").addObject().put("baleId",Ids.next().toString())
                .put("deliveryCode","D1").put("state",checklistState).put("dispatchCycle","1");
        }
        var expected = SubmissionJson.object();
        for (String key : List.of("caseId","caseVersion","status")) expected.set(key,facts.get(key));
        expected.put("userAuthVersion","1");
        command.set("expected",expected);
        return (ObjectNode)command;
    }

    private ObjectNode prepare(HandoverStore store, UUID caseId, UUID warehouse, UUID device, UUID targetUser) {
        var command = caseCommand(store,caseId,warehouse,"PREPARE",null);
        command.put("targetDeviceId",device.toString()).put("targetUserId",targetUser.toString());
        return command;
    }

    private ObjectNode activate(HandoverStore store, UUID caseId, UUID warehouse, UUID device, UUID targetUser) {
        var command = caseCommand(store,caseId,warehouse,"ACTIVATE",null);
        command.put("targetDeviceId",device.toString()).put("targetUserId",targetUser.toString());
        return command;
    }

    private static UUID user(org.springframework.jdbc.core.JdbcTemplate jdbc, UUID warehouse, String permission, String username) {
        UUID id = Ids.next();
        jdbc.update("INSERT INTO app_user(id,username,display_name,password_hash,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(3))",
            Ids.bytes(id),username,"出站验收","x");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(id),Ids.bytes(warehouse));
        jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='WAREHOUSE_ADMIN'",Ids.bytes(id));
        if (permission != null) {
            jdbc.update("INSERT IGNORE INTO role_permission SELECT r.id,p.id FROM role r CROSS JOIN permission p "
                + "WHERE r.code='WAREHOUSE_ADMIN' AND p.code=?",permission);
        }
        return id;
    }

    private static JsonNode call(HandoverCommandService service, ObjectNode command, UUID actorId, String username) throws Exception {
        var actor = new SessionUser(actorId,username,"出站验收","x",1,true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,"unused",List.of()));
        try {
            byte[] response = service.execute(SubmissionJson.canonical(command).getBytes(StandardCharsets.UTF_8),"outbox-check");
            return SubmissionJson.parse(new String(response,StandardCharsets.UTF_8));
        } finally { SecurityContextHolder.clearContext(); }
    }
}
