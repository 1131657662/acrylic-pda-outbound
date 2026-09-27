package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
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
 * R07-1 reduced goal: build a FORMAL, activatable source through the real publish/claim/submit/abort chain and run
 * REQUEST then APPROVE against it. Nothing here touches the TEMP fixtures' state.
 */
public final class HandoverFormalFixtureChecks {
    private HandoverFormalFixtureChecks() { }

    public static void verify(WebApplicationContext context) throws Exception {
        var f = new TemporaryConfirmationFixture(context,false);
        var submission = f.source.f;
        // 1) real FORMAL submission for the published+claimed order, then a real abort (which writes receipt_text).
        submission.submit();
        submission.abort();
        String scope = f.jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM submission_scope WHERE source_order_id=UUID_TO_BIN(?)",
            String.class, submission.order);
        String status = f.jdbc.queryForObject("SELECT status FROM submission WHERE scope_id=UUID_TO_BIN(?)",String.class,scope);
        assertThat(status).as("FORMAL source must end ABORTED with a receipt").isEqualTo("ABORTED");
        assertThat(f.jdbc.queryForObject("SELECT receipt_text IS NOT NULL FROM submission WHERE scope_id=UUID_TO_BIN(?)",Boolean.class,scope))
            .as("ABORTED requires receipt_text").isTrue();

        // 2) handover permission for the acting supervisor, then REQUEST through the real service.
        var actor = f.operator();
        grant(f,actor,"order:handover");
        var store = f.bean(HandoverStore.class);
        var service = f.bean(HandoverCommandService.class);
        var scopeFacts = store.scopeFacts(UUID.fromString(scope));
        assertThat(scopeFacts.path("sourceKind").asText()).as("FORMAL source kind").isEqualTo("FORMAL");
        var request = SubmissionJson.object().put("commandVersion",1).put("commandType","REQUEST")
            .put("commandId",UUID.randomUUID().toString()).put("transportRecoveryEpoch","1").put("reason","原机可用，申请交接")
            .put("warehouseId",scopeFacts.path("warehouseId").asText()).put("scopeId",scope);
        var requestExpected = SubmissionJson.object();
        for(String key:List.of("scopeId","canonicalOrderId","mappingVersion","fromWorkRound")) requestExpected.set(key,scopeFacts.get(key));
        requestExpected.put("userAuthVersion",actor.authVersion()+"");
        request.set("expected",requestExpected);
        var requestReceipt = execute(f,service,request,actor);
        assertThat(requestReceipt.path("status").asText()).as("REQUEST: "+requestReceipt).isEqualTo("RECORDED");
        assertThat(requestReceipt.path("commandType").asText()).isEqualTo("REQUEST");
        String caseId = requestReceipt.path("businessDecision").path("caseId").asText();
        assertThat(caseId).isNotEmpty();
        assertThat(f.jdbc.queryForObject("SELECT status FROM handover_case WHERE id=UUID_TO_BIN(?)",String.class,caseId))
            .isEqualTo("EVIDENCE_PENDING");
        assertThat(f.jdbc.queryForObject("SELECT original_result_state FROM handover_case WHERE id=UUID_TO_BIN(?)",String.class,caseId))
            .as("ABORTED maps to the ABORTED dimension").isEqualTo("ABORTED");

        // 3) the device-produced stop proof (PDA half is a separate sub-item; here it is recorded as data).
        f.jdbc.update("""
            INSERT INTO handover_evidence(id,case_id,kind,device_id,payload_sha256,high_watermark,receipt_bytes,recorded_at)
            VALUES(?,UUID_TO_BIN(?),'STOP_PROOF',UUID_TO_BIN(?),UNHEX(REPEAT('ab',32)),7,UNHEX('00'),UTC_TIMESTAMP(6))
            """,Ids.bytes(Ids.next()),caseId,f.source.a.device());

        // 4) APPROVE: the supervisor binds the four dimensions and the per-bale retained list.
        var caseFacts = store.caseFacts(UUID.fromString(caseId));
        var approve = SubmissionJson.object().put("commandVersion",1).put("commandType","APPROVE")
            .put("commandId",UUID.randomUUID().toString()).put("transportRecoveryEpoch","1").put("reason","主管逐包核对后批准")
            .put("warehouseId",caseFacts.path("warehouseId").asText()).put("caseId",caseId)
            .put("stopState","DEVICE_ACKED").put("integrityState","COMPLETE").put("originalResultState","ABORTED")
            .put("physicalState","MATCHED").put("inputHighWatermark","7");
        var approveExpected = SubmissionJson.object();
        for(String key:List.of("caseId","caseVersion","status")) approveExpected.set(key,caseFacts.get(key));
        approveExpected.put("userAuthVersion",actor.authVersion()+"");
        approve.set("expected",approveExpected);
        var checklist = approve.putArray("checklist");
        checklist.addObject().put("baleId",Ids.next().toString()).put("deliveryCode","D1").put("state","RETAINED").put("dispatchCycle","1");
        checklist.addObject().put("baleId",Ids.next().toString()).put("deliveryCode","D2").put("state","RETAINED").put("dispatchCycle","1");
        var approveReceipt = execute(f,service,approve,actor);
        assertThat(approveReceipt.path("status").asText()).as("APPROVE: "+approveReceipt).isEqualTo("RECORDED");
        assertThat(approveReceipt.path("commandType").asText()).isEqualTo("APPROVE");
        assertThat(f.jdbc.queryForObject("SELECT status FROM handover_case WHERE id=UUID_TO_BIN(?)",String.class,caseId))
            .isEqualTo("APPROVED");
        assertThat(f.jdbc.queryForObject("SELECT approved_by IS NOT NULL FROM handover_case WHERE id=UUID_TO_BIN(?)",Boolean.class,caseId))
            .isTrue();
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM handover_checklist WHERE case_id=UUID_TO_BIN(?)",Integer.class,caseId))
            .isEqualTo(2);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM handover_command_receipt WHERE case_id=UUID_TO_BIN(?)",Integer.class,caseId))
            .isEqualTo(2);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM operation_event WHERE entity_type='HANDOVER_COMMAND'",Integer.class))
            .as("one audit event per receipt").isEqualTo(2);
    }

    private static JsonNode execute(TemporaryConfirmationFixture f,HandoverCommandService service,ObjectNode command,com.acrylic.outbound.auth.SessionUser actor) throws Exception {
        TemporaryConfirmationFixture.authenticate(actor);
        try {
            byte[] receipt=service.execute(SubmissionJson.canonical(command).getBytes(StandardCharsets.UTF_8),"handover-check");
            return SubmissionJson.parse(receipt);
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }

    private static void grant(TemporaryConfirmationFixture f,com.acrylic.outbound.auth.SessionUser actor,String permission) {
        UUID role=Ids.next();
        f.jdbc.update("INSERT INTO role VALUES(?,?,?)",Ids.bytes(role),"HO_"+role.toString().substring(20),"隔离交接权限");
        f.jdbc.update("INSERT INTO role_permission SELECT ?,id FROM permission WHERE code=?",Ids.bytes(role),permission);
        f.jdbc.update("INSERT INTO user_role VALUES(?,?)",Ids.bytes(actor.id()),Ids.bytes(role));
    }
}
