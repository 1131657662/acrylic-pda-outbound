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
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/**
 * R03 round 2: four R07-1 counterexamples driven at the <b>service layer</b> on top of the SQL-built REGISTERED
 * FORMAL source.
 *
 * <p><b>Verification level: service layer (SecurityContextHolder + HandoverCommandService). This is NOT HTTP-level
 * end to end.</b> In particular {@code 非主管审批 → PERMISSION_DENIED} is produced by the service's identity gate;
 * whether the HTTP layer blocks it independently is NOT verified here.
 */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aFormalRegisteredCounterexamplesIT {
    @Test void fourCounterexamplesFailClosedWithoutSideEffects() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "42"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        profile.migrate(jdbc, "44");
        try (var context = E2aCrossSourceHttpIT.start(profile, "--outbound.handover.commands.enabled=true")) {
            runCounterexamples(context, profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "service-layer counterexamples: 未停止/未决未归零/缺审计/非主管审批\n");
    }

    private void runCounterexamples(org.springframework.context.ApplicationContext context,java.nio.file.Path attempt) throws Exception {
        var jdbc = context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        var store = context.getBean(HandoverStore.class);
        var service = context.getBean(HandoverCommandService.class);
        var notes = new StringBuilder("验证层级=服务层（SecurityContextHolder + HandoverCommandService），HTTP 层拦截未验\n");

        var host = jdbc.queryForMap("""
            SELECT BIN_TO_UUID(a.id) assignment_id,BIN_TO_UUID(a.order_id) order_id,BIN_TO_UUID(a.warehouse_id) warehouse_id,
              BIN_TO_UUID(a.device_id) device_id,BIN_TO_UUID(a.operator_id) operator_id,CAST(a.recovery_epoch AS CHAR) recovery_epoch
            FROM order_assignment a WHERE a.status='ACTIVE' LIMIT 1
            """);
        UUID warehouse = UUID.fromString(host.get("warehouse_id").toString());
        UUID order = UUID.fromString(host.get("order_id").toString());
        UUID assignment = UUID.fromString(host.get("assignment_id").toString());
        UUID device = UUID.fromString(host.get("device_id").toString());

        // Approver (order:handover) and an outsider with no handover permission at all.
        // Role split per the kernel: REQUEST needs order:resume, APPROVE needs order:handover.
        UUID requester = user(jdbc, warehouse, "order:resume");
        UUID approver = user(jdbc, warehouse, "order:handover");
        UUID outsider = user(jdbc, warehouse, null);

        UUID scope = UUID.randomUUID(); UUID submission = UUID.randomUUID();
        // FORMAL shape per ck_submission_scope_source: source_order_id set, canonical_order_id MUST stay NULL.
        jdbc.update("INSERT INTO submission_scope(id,warehouse_id,source_order_id,work_round,current_submission_id) VALUES(?,?,?,1,NULL)",
            Ids.bytes(scope),Ids.bytes(warehouse),Ids.bytes(order));
        jdbc.update("INSERT INTO submission(id,scope_id,assignment_id,freeze_device_id,operator_id,work_round,"
            + "source_recovery_epoch,status,issues_json,receipt_text,created_at,updated_at) "
            + "VALUES(?,?,?,?,?,1,?,'REGISTERED',NULL,NULL,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
            Ids.bytes(submission),Ids.bytes(scope),Ids.bytes(assignment),Ids.bytes(device),
            Ids.bytes(UUID.fromString(host.get("operator_id").toString())),Long.parseLong(host.get("recovery_epoch").toString()));
        jdbc.update("UPDATE submission_scope SET current_submission_id=? WHERE id=?",Ids.bytes(submission),Ids.bytes(scope));

        // REQUEST as the approver: this is the only accepted call in this test.
        var scopeFacts = store.scopeFacts(scope);
        var request = SubmissionJson.object().put("commandVersion",1).put("commandType","REQUEST")
            .put("commandId",UUID.randomUUID().toString()).put("transportRecoveryEpoch","1").put("reason","原机可用，申请交接")
            .put("warehouseId",warehouse.toString()).put("scopeId",scope.toString());
        var requestExpected = SubmissionJson.object();
        for (String key : List.of("scopeId","canonicalOrderId","mappingVersion","fromWorkRound")) requestExpected.set(key,scopeFacts.get(key));
        requestExpected.put("userAuthVersion","1");
        request.set("expected",requestExpected);
        JsonNode requestReceipt = execute(service,request,requester);
        assertThat(requestReceipt.path("status").asText()).isEqualTo("RECORDED");
        UUID caseId = UUID.fromString(requestReceipt.path("businessDecision").path("caseId").asText());
        String baselineStatus = jdbc.queryForObject("SELECT status FROM handover_case WHERE id=?",String.class,Ids.bytes(caseId));
        long baselineVersion = jdbc.queryForObject("SELECT row_version FROM handover_case WHERE id=?",Long.class,Ids.bytes(caseId));
        assertThat(baselineStatus).isEqualTo("EVIDENCE_PENDING");

        // ① not stopped ② integrity missing records ③ integrity missing audit
        expect(jdbc,service,approve(store,caseId,warehouse,"REQUESTED","COMPLETE","ABORTED","MATCHED","7"),approver,
            "HANDOVER_STOP_NOT_PROVEN","①未停止",notes);
        expect(jdbc,service,approve(store,caseId,warehouse,"DEVICE_ACKED","MISSING_RECORDS","ABORTED","MATCHED","7"),approver,
            "HANDOVER_INTEGRITY_INCOMPLETE","②未决输入未归零",notes);
        expect(jdbc,service,approve(store,caseId,warehouse,"DEVICE_ACKED","MISSING_AUDIT","ABORTED","MATCHED","7"),approver,
            "HANDOVER_INTEGRITY_INCOMPLETE","③缺审计",notes);
        // ④ approval by someone without the handover permission
        expect(jdbc,service,approve(store,caseId,warehouse,"DEVICE_ACKED","COMPLETE","ABORTED","MATCHED","7"),outsider,
            "PERMISSION_DENIED","④非主管审批",notes);

        // Idempotency (3): replay the ORIGINAL request - same key, same bytes. A second REQUEST for the same scope is
        // NOT an idempotency probe: handover_case carries UNIQUE(scope_id, open_flag) (one open case per source), so it
        // fails as a duplicate key and the kernel answers HANDOVER_RESULT_UNKNOWN, which says nothing about idempotency.
        var first = requestReceipt.toString();
        var again = execute(service,request,requester).toString();
        notes.append("幂等①同键同文 -> ").append(again.equals(first)?"原回执一致 PASS":"不一致 FAIL").append('\n');
        assertThat(again).as("同键同文返回原回执").isEqualTo(first);
        var tampered = request.deepCopy(); tampered.put("reason","篡改后的理由");
        String reuseCode;
        try { execute(service,tampered,requester); reuseCode="NO_ERROR"; }
        catch (BusinessException error) { reuseCode=error.code(); }
        notes.append("幂等②同键异文 -> ").append(reuseCode).append('\n');
        assertThat(reuseCode).as("同键异文应为冲突码（实际记录：%s）",reuseCode)
            .isIn("IDEMPOTENCY_CONTENT_MISMATCH","IDEMPOTENCY_KEY_REUSED","COMMAND_KEY_REUSED","IDEMPOTENCY_CONFLICT",
                "INVALID_COMMAND_KEY_REUSE");
        var recovered = execute(service,request,requester).toString();
        notes.append("幂等③丢响应按原键恢复 -> ").append(recovered.equals(first)?"原回执 PASS":"不一致 FAIL").append('\n');
        assertThat(recovered).as("丢响应按原键恢复原回执").isEqualTo(first);

        assertThat(jdbc.queryForObject("SELECT status FROM handover_case WHERE id=?",String.class,Ids.bytes(caseId))).isEqualTo(baselineStatus);
        assertThat(jdbc.queryForObject("SELECT row_version FROM handover_case WHERE id=?",Long.class,Ids.bytes(caseId))).isEqualTo(baselineVersion);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM handover_command_receipt WHERE case_id=?",Integer.class,Ids.bytes(caseId))).isEqualTo(1);
        Files.writeString(attempt.resolve("counterexamples.txt"),notes.toString());
    }

    private void expect(org.springframework.jdbc.core.JdbcTemplate jdbc,HandoverCommandService service,ObjectNode command,
        UUID actor,String expectedCode,String label,StringBuilder notes) throws Exception {
        String actual;
        try { execute(service,command,actor); actual="NO_ERROR"; }
        catch (BusinessException error) { actual=error.code(); }
        notes.append(label).append(" -> ").append(actual).append('\n');
        assertThat(actual).as(label).isEqualTo(expectedCode);
    }

    private ObjectNode approve(HandoverStore store,UUID caseId,UUID warehouse,String stop,String integrity,
        String originalResult,String physical,String highWatermark) {
        var facts = store.caseFacts(caseId);
        var command = SubmissionJson.object().put("commandVersion",1).put("commandType","APPROVE")
            .put("commandId",UUID.randomUUID().toString()).put("transportRecoveryEpoch","1").put("reason","主管逐包核对后批准")
            .put("warehouseId",warehouse.toString()).put("caseId",caseId.toString())
            .put("stopState",stop).put("integrityState",integrity).put("originalResultState",originalResult)
            .put("physicalState",physical).put("inputHighWatermark",highWatermark);
        var expected = SubmissionJson.object();
        for (String key : List.of("caseId","caseVersion","status")) expected.set(key,facts.get(key));
        expected.put("userAuthVersion","1");
        command.set("expected",expected);
        command.putArray("checklist").addObject().put("baleId",Ids.next().toString())
            .put("deliveryCode","D1").put("state","RETAINED").put("dispatchCycle","1");
        return command;
    }

    private UUID user(org.springframework.jdbc.core.JdbcTemplate jdbc,UUID warehouse,String permission) {
        UUID id = Ids.next();
        jdbc.update("INSERT INTO app_user(id,username,display_name,password_hash,created_at) VALUES(?,?,?,'x',UTC_TIMESTAMP(3))",
            Ids.bytes(id),"handover-"+id,"交接夹具人员");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(id),Ids.bytes(warehouse));
        if (permission != null) {
            UUID role = Ids.next();
            jdbc.update("INSERT INTO role VALUES(?,?,?)",Ids.bytes(role),"HO_"+role.toString().substring(20),"隔离交接审批");
            jdbc.update("INSERT INTO role_permission SELECT ?,id FROM permission WHERE code=?",Ids.bytes(role),permission);
            jdbc.update("INSERT INTO user_role VALUES(?,?)",Ids.bytes(id),Ids.bytes(role));
        }
        return id;
    }

    private JsonNode execute(HandoverCommandService service,ObjectNode command,UUID actorId) throws Exception {
        var user = new SessionUser(actorId,"handover-"+actorId,"交接夹具人员","x",1,true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,"unused",List.of()));
        try {
            return SubmissionJson.parse(service.execute(SubmissionJson.canonical(command).getBytes(StandardCharsets.UTF_8),"handover-counterexample"));
        } finally { SecurityContextHolder.clearContext(); }
    }
}
