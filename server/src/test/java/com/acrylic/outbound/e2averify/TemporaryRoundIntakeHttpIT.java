package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.submissions.SubmissionFiles;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="B4_RETENTION_VERIFY",matches="true")
class TemporaryRoundIntakeHttpIT {
    @Test void actualHttpAcceptsCumulativeRoundAndAtomicallyTransfersRetainedOwners() throws Exception {
        try(var f=new ContinuationReceiptHttpFixture("66",true)) {
            var original=parse(f.app.getBean(SubmissionFiles.class).read(f.database.seed.get("intakePredecessorHash")));
            var commands=new ContinuationReceiptHttpCommands(f);
            var ticket=parse(f.accepted(commands.request(null),"intake-ticket"));
            var activated=parse(f.accepted(commands.request(uuid(ticket.path("businessDecision"),"ticketId")),"intake-activation"));
            var body=new TemporaryRoundIntakeBody(f.database).next(original,activated.path("businessDecision"));
            var before=state(f);var historical=RetentionDatabaseFixture.historical(f.database.jdbc);
            reject(f,body,f.otherToken,"intake-wrong-person",404);reject(f,body,f.otherDeviceToken,"intake-wrong-device",404);
            reject(f,body.deepCopy().put("authorizationId",f.database.seed.get("authorizationId")),f.token,"intake-old-auth",409);
            var prefix=body.deepCopy();((ObjectNode)prefix.path("scans").get(0)).put("weightGrams","999");
            reject(f,prefix,f.token,"intake-prefix-changed",409);assertThat(state(f)).isEqualTo(before);
            permission(f,body);rollback(f,body,before);targetChanged(f,body,before);ownerChanged(f,body,before);
            var accepted=f.continuation(body,f.token,"intake-accepted");
            assertThat(accepted.statusCode()).as(new String(accepted.body())).isEqualTo(200);
            var receipt=parse(accepted.body()).path("data");assertThat(receipt.path("status").asText()).isEqualTo("REVIEW_REQUIRED");
            assertThat(receipt.path("localDeletionAllowed").asBoolean(true)).isFalse();
            assertThat(receipt.path("issues").findValuesAsText("code")).contains("TEMPORARY_ACTIVATION_UNACKNOWLEDGED");
            assertThat(parse(f.continuation(body,f.token,"intake-replay").body()).path("data")).isEqualTo(receipt);
            assertThat(parse(f.readContinuation(text(body,"submissionId"),f.token,"intake-read").body()).path("data")).isEqualTo(receipt);
            reject(f,body.deepCopy().put("temporaryNo","CHANGED"),f.token,"intake-changed-body",409);
            verifyAccepted(f,body,original);assertThat(RetentionDatabaseFixture.historical(f.database.jdbc)).hasSize(historical.size()+1).containsAll(historical);
            Files.writeString(f.database.profile.attempt.resolve("test-completed.marker"),
                "Real HTTP cumulative intake, retained and new K owners, atomic rollback and replay. Synthetic STOP/device/predecessor; no confirmation/accounting or production proof.\n");
        }
    }
    private void reject(ContinuationReceiptHttpFixture f,JsonNode b,String token,String label,int code) throws Exception {
        var response=f.continuation(b,token,label);assertThat(response.statusCode()).as(label+": "+new String(response.body())).isEqualTo(code);
    }
    private String state(ContinuationReceiptHttpFixture f) {
        var j=f.database.jdbc;
        return canonical(object().put("scope",j.queryForList("SELECT HEX(id),work_round,HEX(current_submission_id) FROM submission_scope ORDER BY id").toString())
            .put("stock",f.database.stock().toString()).put("submissions",j.queryForList("SELECT HEX(id),status FROM submission ORDER BY id").toString())
            .put("bindings",j.queryForList("SELECT HEX(ticket_id),HEX(submission_id) FROM temporary_source_continuation_submission").toString())
            .put("payloads",j.queryForList("SELECT HEX(submission_id),payload_sha256 FROM submission_payload ORDER BY submission_id").toString())
            .put("events",j.queryForObject("SELECT COUNT(*) FROM submitted_event",Long.class))
            .put("captures",j.queryForObject("SELECT COUNT(*) FROM submitted_capture",Long.class))
            .put("slots",j.queryForList("SELECT HEX(order_id),HEX(current_submission_id),work_round,slot_version,phase FROM order_submission_slot ORDER BY order_id").toString())
            .put("orders",j.queryForList("SELECT HEX(id),status,row_version FROM outbound_order ORDER BY id").toString())
            .put("outbox",j.queryForObject("SELECT COUNT(*) FROM order_sync_outbox",Long.class))
            .put("audit",j.queryForObject("SELECT COUNT(*) FROM operation_event",Long.class)));
    }
    private void rollback(ContinuationReceiptHttpFixture f,ObjectNode body,String before) throws Exception {
        f.database.jdbc.execute("CREATE TRIGGER intake_final_failure BEFORE INSERT ON operation_event FOR EACH ROW BEGIN "
            +"IF NEW.event_type='SUBMISSION_REVIEW_REQUIRED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='intake final audit failure'; END IF; END");
        try { reject(f,body,f.token,"intake-final-audit-failure",500);assertThat(state(f)).isEqualTo(before); }
        finally { f.database.jdbc.execute("DROP TRIGGER intake_final_failure"); }
    }
    private void targetChanged(ContinuationReceiptHttpFixture f,ObjectNode body,String before) throws Exception {
        f.database.jdbc.update("UPDATE order_line SET planned_count=5 WHERE revision_id IN (SELECT id FROM order_revision WHERE order_id=UUID_TO_BIN(?))",f.database.seed.get("orderId"));
        try { reject(f,body,f.token,"intake-target-plan-changed",409);assertThat(state(f)).isEqualTo(before); }
        finally { f.database.jdbc.update("UPDATE order_line SET planned_count=4 WHERE revision_id IN (SELECT id FROM order_revision WHERE order_id=UUID_TO_BIN(?))",f.database.seed.get("orderId")); }
    }
    private void permission(ContinuationReceiptHttpFixture f,ObjectNode body) throws Exception {
        var j=f.database.jdbc;
        byte[] permission=j.queryForObject("SELECT id FROM permission WHERE code='pda:submit'",byte[].class);
        var roles=j.queryForList("SELECT role_id FROM role_permission WHERE permission_id=?",byte[].class,permission);
        j.update("DELETE FROM role_permission WHERE permission_id=?",permission);
        try { reject(f,body,f.token,"intake-no-permission",403); }
        finally { for(var role:roles)j.update("INSERT INTO role_permission VALUES(?,?)",role,permission); }
    }
    private void ownerChanged(ContinuationReceiptHttpFixture f,ObjectNode body,String before) throws Exception {
        var j=f.database.jdbc;String bale=f.database.seed.get("reservedBaleId");
        j.update("UPDATE bale SET dispatch_cycle=2 WHERE id=UUID_TO_BIN(?)",bale);
        try { reject(f,body,f.token,"intake-retained-cycle-changed",409);assertThat(state(f)).isEqualTo(before); }
        finally { j.update("UPDATE bale SET dispatch_cycle=1 WHERE id=UUID_TO_BIN(?)",bale); }
    }
    private void verifyAccepted(ContinuationReceiptHttpFixture f,ObjectNode body,JsonNode original) {
        var j=f.database.jdbc;String id=text(body,"submissionId");
        assertThat(j.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE submission_id=UUID_TO_BIN(?) AND state='REVIEW_RESERVED' AND temporary_continuation_id IS NULL AND source_evidence_set_id IS NULL",Integer.class,id)).isEqualTo(4);
        assertThat(j.queryForObject("SELECT status FROM temporary_source_continuation",String.class)).isEqualTo("ACTIVATED");
        assertThat(j.queryForObject("SELECT work_round FROM submission_scope WHERE current_submission_id=UUID_TO_BIN(?)",Integer.class,id)).isEqualTo(2);
        assertThat(j.queryForObject("SELECT work_round FROM order_submission_slot WHERE current_submission_id=UUID_TO_BIN(?)",Integer.class,id)).isEqualTo(2);
        assertThat(parse(f.app.getBean(SubmissionFiles.class).read(f.database.seed.get("intakePredecessorHash")))).isEqualTo(original);
        assertThat(j.queryForObject("SELECT status FROM submission WHERE id=UUID_TO_BIN(?)",String.class,text(original,"submissionId"))).isEqualTo("ABORTED");
    }
}
