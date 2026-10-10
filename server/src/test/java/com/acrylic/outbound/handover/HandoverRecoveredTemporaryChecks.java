package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

/** Actual existing handover effects commit under original custody, while failures leave every business table unchanged. */
public final class HandoverRecoveredTemporaryChecks {
    public record Bindings(HandoverRecoveredTemporaryBinding original,HandoverRecoveredTemporaryBinding foreign) { }
    public static void verify(WebApplicationContext app,Path attempt,Bindings bindings) throws Exception {
        var f=new HandoverRecoveredTemporaryFixture(app,attempt,bindings.original());
        var request=f.request(); ordinaryRefused(f,request);
        HandoverRecoveredTemporaryHttpRefusal.verify(f,request);
        HandoverRecoveredTemporaryAdversarialChecks.boundaries(f,request,bindings.foreign());
        var initial=f.snapshot("before");
        refused(f,f.supervisor,request.deepCopy().put("scopeId",UUID.randomUUID().toString()),"HANDOVER_RECOVERED_SOURCE_MISMATCH");
        refused(f,f.supervisor,request.deepCopy().put("transportRecoveryEpoch","1"),"HANDOVER_CONTEXT_CHANGED");
        byte[] requested=f.execute(f.requester,request); Files.write(f.directory.resolve("request-command.json"),HandoverReceiptCodec.bytes(request));
        Files.write(f.directory.resolve("request-receipt.json"),requested); UUID caseId=uuid(parse(requested).path("businessDecision"),"caseId");
        assertThat(f.execute(f.requester,request)).isEqualTo(requested);
        var fault=f.fault(caseId); refusalChecks(f,fault); rollback(f,fault);
        HandoverRecoveredTemporaryAdversarialChecks.verificationTail(f,fault);
        byte[] receipt=f.execute(f.supervisor,fault); Files.write(f.directory.resolve("fault-command.json"),HandoverReceiptCodec.bytes(fault));
        Files.write(f.directory.resolve("fault-receipt.json"),receipt); assertThat(f.execute(f.supervisor,fault)).isEqualTo(receipt);
        HandoverRecoveredTemporaryAdversarialChecks.projection(f,fault);
        var after=f.snapshot("after"); conserved(initial,after); fenced(f,caseId,parse(receipt));
        assertThat(f.jdbc.queryForObject("SELECT service_mode FROM server_state WHERE id=1",String.class)).isEqualTo("RECOVERY_PENDING");
        refused(f,f.supervisor,fault.deepCopy().put("commandType","ACTIVATE"),"HANDOVER_RECOVERED_SOURCE_MISMATCH");
        Files.writeString(f.directory.resolve("result.json"),object().put("originalSourcePreserved",true).put("faultFenced",true)
            .put("ordinaryWritesAllowed",false).put("localDeletionAllowed",false).put("caseId",caseId.toString()).toPrettyString());
    }

    private static void ordinaryRefused(HandoverRecoveredTemporaryFixture f,JsonNode request) {
        HandoverRecoveredTemporaryFixture.authenticate(f.requester);
        try { assertThatThrownBy(()->f.commands.execute(HandoverReceiptCodec.bytes(request),"ordinary-recovery"))
            .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo("SERVER_RECOVERY_REQUIRED")); }
        finally { SecurityContextHolder.clearContext(); }
    }

    private static void refusalChecks(HandoverRecoveredTemporaryFixture f,ObjectNode fault) throws Exception {
        refused(f,f.requester,fault,"HANDOVER_SELF_APPROVAL_DENIED");
        refused(f,f.unprivileged,fault,"PERMISSION_DENIED");
        var wrong=fault.deepCopy(); ((ObjectNode)wrong.path("archivedEvidence")).put("verificationId",UUID.randomUUID().toString());
        refused(f,f.supervisor,wrong,"HANDOVER_RECOVERED_SOURCE_MISMATCH");
        var uncontrolled=fault.deepCopy(); ((ObjectNode)uncontrolled.path("physicalControl")).put("unresolvedInputCount","1");
        refused(f,f.supervisor,uncontrolled,"HANDOVER_FAULT_NOT_CONTROLLED");
    }

    private static void refused(HandoverRecoveredTemporaryFixture f,com.acrylic.outbound.auth.SessionUser actor,JsonNode command,String code) throws Exception {
        var before=f.snapshots.capture();
        assertThatThrownBy(()->f.execute(actor,command)).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo(code));
        assertThat(f.snapshots.capture()).isEqualTo(before);
    }

    private static void rollback(HandoverRecoveredTemporaryFixture f,ObjectNode command) throws Exception {
        var before=f.snapshots.capture();
        f.jdbc.execute("CREATE TRIGGER received_fault_tail BEFORE INSERT ON operation_event FOR EACH ROW BEGIN "
            +"IF NEW.event_type='HANDOVER_FAULT_FENCE' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='RECEIVED_FAULT_TAIL'; END IF; END");
        try { assertThatThrownBy(()->f.execute(f.supervisor,command)).hasMessageContaining("RECEIVED_FAULT_TAIL"); }
        finally { f.jdbc.execute("DROP TRIGGER received_fault_tail"); }
        assertThat(f.snapshots.capture()).isEqualTo(before);
    }

    private static void fenced(HandoverRecoveredTemporaryFixture f,UUID caseId,JsonNode receipt) {
        var source=f.binding.request().evidence().expected(); var decision=receipt.path("businessDecision");
        assertThat(decision.path("status").asText()).isEqualTo("FAULT_FENCED"); assertThat(decision.path("oldDeviceAcknowledged").booleanValue()).isFalse();
        assertThat(receipt.path("actor").path("principalId").asText()).isEqualTo(f.supervisor.id().toString());
        assertThat(receipt.path("capabilities")).isEqualTo(HandoverReceiptCodec.capabilities());
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM handover_fault_fence WHERE case_id=?",Integer.class,Ids.bytes(caseId))).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("SELECT status FROM device WHERE id=?",String.class,Ids.bytes(source.deviceId()))).isEqualTo("DISABLED");
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM pda_session WHERE device_id=? AND revoked_at IS NOT NULL",Integer.class,Ids.bytes(source.deviceId()))).isGreaterThan(0);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM order_sync_outbox WHERE event_type='HANDOVER_FAULT_FENCED' AND order_id=?",Integer.class,Ids.bytes(source.canonicalOrderId()))).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM handover_evidence WHERE case_id=?",Integer.class,Ids.bytes(caseId))).isZero();
    }

    private static void conserved(JsonNode before,JsonNode after) {
        var changed=Set.of("api_idempotency_request","audit_event","operation_event","handover_case","handover_command_receipt",
            "handover_fault_fence","device","pda_session","order_sync_outbox","outbound_order");
        before.path("tables").fields().forEachRemaining(entry->{
            if(!changed.contains(entry.getKey())) assertThat(after.path("tables").path(entry.getKey())).as(entry.getKey()).isEqualTo(entry.getValue());
        });
        before.path("schema").fields().forEachRemaining(entry->{
            String expected=entry.getValue().asText(),actual=after.path("schema").path(entry.getKey()).asText();
            if(entry.getKey().equals("operation_event")) { expected=expected.replaceAll(" AUTO_INCREMENT=[0-9]+",""); actual=actual.replaceAll(" AUTO_INCREMENT=[0-9]+",""); }
            assertThat(actual).as(entry.getKey()).isEqualTo(expected);
        });
        for(String field:new String[]{"files","flywayHistory"}) assertThat(after.path(field)).isEqualTo(before.path(field));
    }
}
