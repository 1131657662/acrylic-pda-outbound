package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual HTTP consumers operate on the preserved signed Android source, with new synthetic reviewer identities. */
public final class QuarantineHttpChecks {
    private QuarantineHttpChecks() { }
    public static void prepareBrowser(WebApplicationContext context,Path attempt) throws Exception { new QuarantineHttpFixture(context,attempt); }
    public static void verify(WebApplicationContext context,Path attempt) throws Exception {
        var f=new QuarantineHttpFixture(context,attempt); var originals=originals(f);
        QuarantineHttpRejections.origin(f);
        var discovery=f.command("DISCOVER",f.maker);
        f.caseId=SubmissionJson.parse(f.signed(f.maker,discovery)).at("/businessDecision/caseId").asText();
        var source=f.jdbc.queryForObject("SELECT original_response_bytes FROM temporary_command_receipt WHERE command_type=?",byte[].class,f.sourceCommandType);
        assertThat(f.maker.get("/corrections/quarantine/original-receipt?warehouseId="+f.warehouse+"&commandType=RECONCILE_QUARANTINE_SUBMIT&caseId="+f.caseId,200)).containsExactly(source);
        var submission=f.command("SUBMIT",f.maker);
        var wrong=submission.deepCopy(); ((ObjectNode)wrong.path("evidence")).put("observation","RETURNED_IN_WAREHOUSE");
        f.maker.command(wrong,400); QuarantineHttpRejections.evidence(f,submission); f.signed(f.maker,submission);
        assertThat(f.checker.read(f.warehouse,f.attachment.path("attachmentId").asText(),true,200))
            .containsExactly(java.nio.file.Files.readAllBytes(f.evidence.resolve("inspection-original.bin")));
        assertThat(f.checker.get("/corrections/quarantine/original-receipt?warehouseId="+f.warehouse+"&commandType=RECONCILE_QUARANTINE_APPROVE&caseId="+f.caseId,200)).containsExactly(source);
        refusal(f); var approval=f.command("APPROVE",f.checker); QuarantineHttpRejections.approval(f,approval); rollback(f,approval);
        byte[] receipt=f.signed(f.checker,approval); verifyAfter(f,receipt,originals); concurrency(f,approval,receipt);
        var mismatch=approval.deepCopy().put("reason","changed original intent"); f.checker.command(mismatch,409);
        f.checker.command(approval.deepCopy().put("commandId",UUID.randomUUID().toString()),409);
        assertThat(f.checker.get("/corrections/commands/"+approval.path("commandId").asText()+"?warehouseId="+f.warehouse
            +"&commandType=RECONCILE_QUARANTINE_APPROVE",200)).containsExactly(receipt);
        var legacy=SubmissionJson.parse(f.maker.get("/corrections/reconciliation?warehouseId="+f.warehouse,200));
        assertThat(legacy.path("cases")).isEmpty();
        var list=SubmissionJson.parse(f.checker.get("/corrections/quarantine?warehouseId="+f.warehouse+"&review=true",200));
        assertThat(list.path("candidates")).isEmpty(); assertThat(list.path("cases").size()).isEqualTo(1);
        assertThat(list.path("nextCaseCursor").isNull()).isTrue();
        com.acrylic.outbound.correction.QuarantinePagingChecks.verify(context,f.warehouse,f.makerUser);
    }
    private static void refusal(QuarantineHttpFixture f) throws Exception {
        var approval=f.command("APPROVE",f.checker);
        f.maker.command(approval,403);
        makerApproval(f,true); f.maker.command(approval,403);
        makerApproval(f,false); f.maker.command(approval,403);
        f.checker.command(approval.deepCopy().put("warehouseId",UUID.randomUUID().toString()),404);
        f.jdbc.update("UPDATE inventory_position SET row_version=row_version+1 WHERE bale_id=UUID_TO_BIN(?)",f.bale);
        f.checker.command(approval,409);
        f.jdbc.update("UPDATE inventory_position SET row_version=row_version-1 WHERE bale_id=UUID_TO_BIN(?)",f.bale);
        var legacy=SubmissionJson.object().put("commandVersion",1).put("commandType","RECONCILE_ADVANCE").put("commandId",UUID.randomUUID().toString())
            .put("warehouseId",f.warehouse).put("transportRecoveryEpoch","1").put("caseId",f.caseId).put("conclusion","cannot clear by state transition");
        legacy.putObject("expected").put("caseId",f.caseId).put("status","REVIEW_READY").put("rowVersion","2").put("userAuthVersion","1");
        f.maker.command(legacy,409);
    }
    private static void makerApproval(QuarantineHttpFixture f,boolean grant) {
        if(grant) f.jdbc.update("INSERT INTO role_permission SELECT ur.role_id,p.id FROM user_role ur JOIN app_user u ON u.id=ur.user_id CROSS JOIN permission p WHERE u.username=? AND p.code='correction:approve'",f.makerUser);
        else f.jdbc.update("DELETE rp FROM role_permission rp JOIN user_role ur ON ur.role_id=rp.role_id JOIN app_user u ON u.id=ur.user_id JOIN permission p ON p.id=rp.permission_id WHERE u.username=? AND p.code='correction:approve'",f.makerUser);
    }
    private static void rollback(QuarantineHttpFixture f,ObjectNode approval) throws Exception {
        f.jdbc.execute("CREATE TRIGGER quarantine_tail_fail BEFORE INSERT ON operation_event FOR EACH ROW BEGIN IF NEW.event_type='CORRECTION_RECONCILE_QUARANTINE_APPROVE' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ISOLATED tail failure'; END IF; END");
        f.checker.command(approval,500); f.jdbc.execute("DROP TRIGGER quarantine_tail_fail");
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE bale_id=UUID_TO_BIN(?) AND state='QUARANTINED'",Integer.class,f.bale)).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM quarantine_release",Integer.class)).isZero();
        assertThat(f.jdbc.queryForObject("SELECT status FROM inventory_reconciliation WHERE id=UUID_TO_BIN(?)",String.class,f.caseId)).isEqualTo("REVIEW_READY");
    }
    private static void verifyAfter(QuarantineHttpFixture f,byte[] receipt,Map<String,List<String>> before) {
        var origin=SubmissionJson.parse(receipt).at("/context/facts/origin");
        assertThat(SubmissionJson.parse(receipt).path("orderId")).isEqualTo(origin.path("orderId"));
        assertThat(originals(f)).isEqualTo(before);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE bale_id=UUID_TO_BIN(?)",Integer.class,f.bale)).isZero();
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM quarantine_release",Integer.class)).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("SELECT dispatch_cycle FROM bale WHERE id=UUID_TO_BIN(?)",Integer.class,f.bale)).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("SELECT book_status FROM inventory_position WHERE bale_id=UUID_TO_BIN(?)",String.class,f.bale)).isEqualTo("NOT_DEDUCTED");
    }
    private static Map<String,List<String>> originals(QuarantineHttpFixture f) {
        var result=new TreeMap<String,List<String>>();
        for(String table:List.of("temporary_physical_disposition","temporary_physical_disposition_item","temporary_command_receipt",
            "source_evidence_set","source_evidence_verification","source_evidence_item","submission","submission_scope","order_submission_slot",
            "dispatch_record","inventory_ledger","correction_ledger","order_result_version","inventory_position"))
            result.put(table,f.jdbc.queryForList("SELECT * FROM "+table).stream().map(QuarantineHttpChecks::encodeRow).sorted().toList());
        result.put("otherOwners",f.jdbc.queryForList("SELECT * FROM bale_commitment WHERE bale_id<>UUID_TO_BIN(?)",f.bale)
            .stream().map(QuarantineHttpChecks::encodeRow).sorted().toList());
        return result;
    }
    private static String encodeRow(Map<String,Object> row) {
        var encoded=SubmissionJson.object();
        new TreeMap<>(row).forEach((column,value)-> {
            if(value==null) encoded.putNull(column);
            else encoded.putObject(column).put("type",value.getClass().getName()).put("value",
                value instanceof byte[] bytes ? Base64.getEncoder().encodeToString(bytes) : value.toString());
        });
        return SubmissionJson.canonical(encoded);
    }
    private static void concurrency(QuarantineHttpFixture f,ObjectNode command,byte[] original) throws Exception {
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->f.checker.command(command,200)); var second=pool.submit(()->f.checker.command(command,200));
            assertThat(first.get()).containsExactly(original); assertThat(second.get()).containsExactly(original);
        }
    }
}
