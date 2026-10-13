package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

public final class QuarantineHistoryHttpChecks {
    private final QuarantineHistoryHttpSource f;
    private String caseId;
    public QuarantineHistoryHttpChecks(QuarantineHistoryHttpSource source) { f=source; }
    public void verify() throws Exception {
        var before=originals();var facts=preparation("DISCOVER",f.maker).at("/context/facts");
        assertThat(facts.path("history")).hasSize(1);
        assertThat(facts.at("/history/0/dispatch/count").asText()).isEqualTo("0");
        assertThat(facts.at("/history/0/stockRestorationReceipt/commandType").asText()).isEqualTo("RECONCILE_RETURN_APPROVE");
        historyDownloads(facts.path("history").get(0));
        byte[] source=f.maker.get(target("original-receipt","DISCOVER"),200);Files.write(f.evidence.resolve("original-pc-proof.json"),source);
        caseId=SubmissionJson.parse(signed("DISCOVER",f.maker,null)).at("/businessDecision/caseId").asText();
        byte[] inspection="ISOLATED returned bale inspection and clearance".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(f.evidence.resolve("inspection-original.bin"),inspection);
        var attachment=f.maker.upload(f.warehouse,UUID.randomUUID().toString(),inspection,CorrectionAttachmentHttp.sha(inspection),200,false).path("data");
        var evidence=SubmissionJson.object().put("observedAt",Instant.now().minusSeconds(3).toString()).put("place","ISOLATED warehouse bay")
            .put("reference","ISOLATED same barcode conditions cleared").put("observation","QUARANTINE_CLEARED_IN_WAREHOUSE");
        evidence.putArray("attachments").add(attachment);signed("SUBMIT",f.maker,evidence);
        assertThat(f.checker.read(f.warehouse,attachment.path("attachmentId").asText(),true,200)).containsExactly(inspection);
        var approval=command("APPROVE",f.checker);f.maker.command(approval,403);refuseDamagedHistory(approval);rollback(approval);
        byte[] receipt=f.checker.command(approval,200);save(approval,receipt);concurrency(approval,receipt);
        f.checker.command(approval.deepCopy().put("reason","changed same key"),409);
        assertThat(originals()).isEqualTo(before);assertAfter();
        assertThat(f.checker.get(target("original-receipt","APPROVE"),200)).containsExactly(source);
        Files.writeString(f.evidence.resolve("restart-input.json"),SubmissionJson.canonical(approval));
    }
    private void historyDownloads(JsonNode item) throws Exception {
        String path=target("original-receipt","DISCOVER");String dispatch=item.at("/dispatch/dispatchId").asText();
        for(String kind:java.util.List.of("return","restoration")) {
            String pointer=kind.equals("return")?"/reopening/approvalReceipt":"/stockRestorationReceipt";
            var original=item.at(pointer);byte[] bytes=f.maker.get(path+"&historyDispatchId="+dispatch+"&historyReceiptType="+kind,200);
            assertThat(bytes).containsExactly(Files.readAllBytes(f.returned.original.evidence.resolve(original.path("commandId").asText()+"-receipt.json")));
            SupervisorCorrectionReceipt.verify(bytes,f.returned.original.publicKey);
            Files.write(f.evidence.resolve("history-"+kind+"-original.json"),bytes);
        }
        f.maker.get(path+"&historyDispatchId="+dispatch,404);
        f.maker.get(path+"&historyReceiptType=return",404);
        f.maker.get(path+"&historyDispatchId="+UUID.randomUUID()+"&historyReceiptType=return",404);
        f.maker.get(path+"&historyDispatchId="+dispatch+"&historyReceiptType=other",404);
    }
    private void refuseDamagedHistory(ObjectNode approval) throws Exception {
        var jdbc=f.returned.original.jdbc;
        assertThatThrownBy(()->jdbc.update("UPDATE correction_command_receipt SET response_sha256=UNHEX(REPEAT('0',64)) WHERE document_id=UUID_TO_BIN(?)",f.returned.document))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE correction_ledger SET reverses_inventory_entry_id=NULL WHERE dispatch_id=UUID_TO_BIN(?)",f.returned.original.dispatch))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM returned_stock_release WHERE original_dispatch_id=UUID_TO_BIN(?)",f.returned.original.dispatch))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        jdbc.update("UPDATE inventory_position SET book_status='DEDUCTED' WHERE bale_id=UUID_TO_BIN(?)",f.bale);
        f.checker.command(approval,409);jdbc.update("UPDATE inventory_position SET book_status='NOT_DEDUCTED' WHERE bale_id=UUID_TO_BIN(?)",f.bale);
        assertThat(jdbc.queryForObject("SELECT state FROM bale_commitment WHERE bale_id=UUID_TO_BIN(?)",String.class,f.bale)).isEqualTo("QUARANTINED");
    }
    private void rollback(ObjectNode approval) throws Exception {
        var jdbc=f.returned.original.jdbc;
        jdbc.execute("CREATE TRIGGER history_quarantine_tail_fail BEFORE INSERT ON operation_event FOR EACH ROW BEGIN IF NEW.event_type='CORRECTION_RECONCILE_QUARANTINE_APPROVE' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ISOLATED tail fail'; END IF; END");
        f.checker.command(approval,500);jdbc.execute("DROP TRIGGER history_quarantine_tail_fail");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quarantine_release",Integer.class)).isZero();
    }
    private void concurrency(ObjectNode command,byte[] original) throws Exception {
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->f.checker.command(command,200));var second=pool.submit(()->f.checker.command(command,200));
            assertThat(first.get()).containsExactly(original);assertThat(second.get()).containsExactly(original);
        }
    }
    private void assertAfter() {
        var jdbc=f.returned.original.jdbc;
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE bale_id=UUID_TO_BIN(?)",Integer.class,f.bale)).isZero();
        assertThat(jdbc.queryForObject("SELECT dispatch_cycle FROM bale WHERE id=UUID_TO_BIN(?)",Integer.class,f.bale)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT book_status FROM inventory_position WHERE bale_id=UUID_TO_BIN(?)",String.class,f.bale)).isEqualTo("NOT_DEDUCTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quarantine_release",Integer.class)).isEqualTo(1);
    }
    private ObjectNode originals() {
        var result=SubmissionJson.object();var jdbc=f.returned.original.jdbc;
        for(String table:java.util.List.of("dispatch_record","inventory_ledger","correction_ledger","order_result_version","cycle_reopening","returned_stock_release","inventory_position","pc_disposition"))
            result.set(table,new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().valueToTree(jdbc.queryForList("SELECT * FROM "+table)));
        return result;
    }
    private String target(String resource,String kind) { return "/corrections/quarantine/"+resource+"?warehouseId="+f.warehouse+"&commandType=RECONCILE_QUARANTINE_"+kind
        +(kind.equals("DISCOVER")?"&baleId="+f.bale:"&caseId="+caseId); }
    private JsonNode preparation(String kind,CorrectionAttachmentHttp actor) throws Exception { return SubmissionJson.parse(actor.get(target("context",kind),200)); }
    private ObjectNode command(String kind,CorrectionAttachmentHttp actor) throws Exception {
        var body=SubmissionJson.object().put("commandVersion",1).put("commandType","RECONCILE_QUARANTINE_"+kind)
            .put("commandId",UUID.randomUUID().toString()).put("warehouseId",f.warehouse).put("transportRecoveryEpoch","1").put("reason","ISOLATED prior returned cycle clearance");
        body.set("expected",preparation(kind,actor).path("expected"));if(kind.equals("DISCOVER"))body.put("baleId",f.bale);else body.put("caseId",caseId);return body;
    }
    private byte[] signed(String kind,CorrectionAttachmentHttp actor,JsonNode evidence) throws Exception {
        var body=command(kind,actor);if(evidence!=null)body.set("evidence",evidence);byte[] bytes=actor.command(body,200);save(body,bytes);return bytes;
    }
    private void save(ObjectNode command,byte[] bytes) throws Exception {
        SupervisorCorrectionReceipt.verify(bytes,f.returned.original.publicKey);
        Files.writeString(f.evidence.resolve(command.path("commandId").asText()+"-command.json"),SubmissionJson.canonical(command));
        Files.write(f.evidence.resolve(command.path("commandId").asText()+"-receipt.json"),bytes);
    }
}
