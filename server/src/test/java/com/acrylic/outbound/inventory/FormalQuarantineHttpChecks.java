package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Real session/CSRF clearance consuming immutable original FORMAL approvals and signed application. */
public final class FormalQuarantineHttpChecks {
    private final FormalQuarantineHttpSource source;
    private final CorrectionAttachmentHttp maker,checker;
    private final Path publicKey;
    private String caseId;
    public FormalQuarantineHttpChecks(FormalQuarantineHttpSource source,Path attempt) throws Exception {
        this.source=source;publicKey=attempt.resolve("private/server-public.der");
        maker=new CorrectionAttachmentHttp(source.makerUser,source.evidence.resolve("maker-clearance.log"));
        checker=new CorrectionAttachmentHttp(source.checkerUser,source.evidence.resolve("checker-clearance.log"));
    }
    public void verify() throws Exception {
        var originals=source.originals();var discovery=command("DISCOVER",maker);var prepared=preparation("DISCOVER",maker);
        var origin=prepared.at("/context/facts/origin");assertThat(origin.path("sourceKind").asText()).isEqualTo("FORMAL");
        assertThat(origin.at("/originalReceipt/proofType").asText()).isEqualTo("FORMAL_RECOVERY_APPLICATION_ORIGINALS");
        byte[] proof=maker.get(target("original-receipt","DISCOVER"),200);assertThat(CorrectionAttachmentHttp.sha(proof)).isEqualTo(origin.path("originalReceiptSha256").asText());
        Files.write(source.evidence.resolve("original-formal-proof.json"),proof);
        FormalQuarantineHttpTampering.verify(source,maker,target("context","DISCOVER"),origin);
        caseId=SubmissionJson.parse(signed(maker,discovery)).at("/businessDecision/caseId").asText();
        byte[] inspection=("ISOLATED FORMAL physical inspection "+source.bale).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var attachment=maker.upload(source.warehouse,UUID.randomUUID().toString(),inspection,CorrectionAttachmentHttp.sha(inspection),200,false).path("data");
        var submit=command("SUBMIT",maker);var evidence=submit.putObject("evidence").put("observedAt",Instant.now().minusSeconds(3).toString())
            .put("place","ISOLATED original FORMAL warehouse bay").put("reference","ISOLATED isolation reason cleared and physical stock verified")
            .put("observation","QUARANTINE_CLEARED_IN_WAREHOUSE");evidence.putArray("attachments").add(attachment);signed(maker,submit);
        assertThat(checker.read(source.warehouse,attachment.path("attachmentId").asText(),true,200)).containsExactly(inspection);
        var approval=command("APPROVE",checker);selfApproval(approval);checker.command(approval.deepCopy().put("warehouseId",UUID.randomUUID().toString()),404);rollback(approval);
        byte[] receipt=signed(checker,approval);concurrency(approval,receipt);checker.command(approval.deepCopy().put("reason","different intent"),409);
        assertThat(source.originals()).isEqualTo(originals);assertAfter();assertThat(checker.get(target("original-receipt","APPROVE"),200)).containsExactly(proof);
        Files.writeString(source.evidence.resolve("restart-input.json"),SubmissionJson.canonical(approval));
    }
    private void assertAfter() {
        assertThat(source.jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE bale_id=UUID_TO_BIN(?)",Integer.class,source.bale)).isZero();
        assertThat(source.jdbc.queryForObject("SELECT COUNT(*) FROM quarantine_release WHERE case_id=UUID_TO_BIN(?)",Integer.class,caseId)).isEqualTo(1);
        assertThat(source.jdbc.queryForObject("SELECT dispatch_cycle FROM bale WHERE id=UUID_TO_BIN(?)",Integer.class,source.bale)).isEqualTo(1);
        assertThat(source.jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_record WHERE bale_id=UUID_TO_BIN(?)",Integer.class,source.bale)).isZero();
        assertThat(source.jdbc.queryForObject("SELECT COUNT(*) FROM inventory_ledger WHERE bale_id=UUID_TO_BIN(?)",Integer.class,source.bale)).isZero();
        assertThat(source.jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_record WHERE order_id=UUID_TO_BIN(?)",Integer.class,source.order)).isEqualTo(1);
    }
    private void selfApproval(ObjectNode approval) throws Exception {
        source.jdbc.update("INSERT INTO user_role SELECT u.id,r.id FROM app_user u CROSS JOIN role r WHERE u.username=? AND r.code='SUPERVISOR'",source.makerUser);
        assertThat(SubmissionJson.parse(maker.command(approval,403)).path("code").asText()).isEqualTo("CORRECTION_SELF_APPROVAL_DENIED");
        source.jdbc.update("DELETE ur FROM user_role ur JOIN app_user u ON u.id=ur.user_id JOIN role r ON r.id=ur.role_id WHERE u.username=? AND r.code='SUPERVISOR'",source.makerUser);
    }
    private void rollback(ObjectNode approval) throws Exception {
        source.jdbc.execute("CREATE TRIGGER formal_quarantine_tail_fail BEFORE INSERT ON operation_event FOR EACH ROW BEGIN IF NEW.event_type='CORRECTION_RECONCILE_QUARANTINE_APPROVE' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ISOLATED FORMAL tail failure'; END IF; END");
        checker.command(approval,500);source.jdbc.execute("DROP TRIGGER formal_quarantine_tail_fail");
        assertThat(source.jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE bale_id=UUID_TO_BIN(?)",Integer.class,source.bale)).isEqualTo(1);
        assertThat(source.jdbc.queryForObject("SELECT COUNT(*) FROM quarantine_release WHERE case_id=UUID_TO_BIN(?)",Integer.class,caseId)).isZero();
    }
    private void concurrency(ObjectNode command,byte[] receipt) throws Exception {
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->checker.command(command,200));var b=pool.submit(()->checker.command(command,200));assertThat(a.get()).containsExactly(receipt);assertThat(b.get()).containsExactly(receipt);
        }
    }
    private ObjectNode command(String kind,CorrectionAttachmentHttp actor) throws Exception {
        var command=SubmissionJson.object().put("commandVersion",1).put("commandType","RECONCILE_QUARANTINE_"+kind).put("commandId",UUID.randomUUID().toString())
            .put("warehouseId",source.warehouse).put("transportRecoveryEpoch","1").put("reason","ISOLATED independent FORMAL clearance");
        command.set("expected",preparation(kind,actor).path("expected"));if(kind.equals("DISCOVER"))command.put("baleId",source.bale);else command.put("caseId",caseId);return command;
    }
    private JsonNode preparation(String kind,CorrectionAttachmentHttp actor) throws Exception { return SubmissionJson.parse(actor.get(target("context",kind),200)); }
    private String target(String resource,String kind) {
        return "/corrections/quarantine/"+resource+"?warehouseId="+source.warehouse+"&commandType=RECONCILE_QUARANTINE_"+kind+(kind.equals("DISCOVER")?"&baleId="+source.bale:"&caseId="+caseId);
    }
    private byte[] signed(CorrectionAttachmentHttp actor,ObjectNode command) throws Exception {
        byte[] bytes=actor.command(command,200);SupervisorCorrectionReceipt.verify(bytes,publicKey);
        Files.write(source.evidence.resolve(command.path("commandId").asText()+"-receipt.json"),bytes);
        Files.writeString(source.evidence.resolve(command.path("commandId").asText()+"-command.json"),SubmissionJson.canonical(command));assertThat(actor.command(command,200)).containsExactly(bytes);return bytes;
    }
}
