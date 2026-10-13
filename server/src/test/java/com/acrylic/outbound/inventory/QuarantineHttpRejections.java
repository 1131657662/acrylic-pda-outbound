package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

/** Faults only touch the new isolated copy, restoring every original value before positive consumption. */
final class QuarantineHttpRejections {
    private QuarantineHttpRejections() { }
    static void origin(QuarantineHttpFixture f) throws Exception {
        var row=f.jdbc.queryForMap("SELECT id,signed_receipt FROM temporary_command_receipt WHERE command_type=?",f.sourceCommandType);
        String signature=row.get("signed_receipt").toString();
        f.jdbc.update("UPDATE temporary_command_receipt SET signed_receipt=? WHERE id=?",signature+"x",row.get("id"));
        try { f.maker.get("/corrections/quarantine/context?warehouseId="+f.warehouse+"&commandType=RECONCILE_QUARANTINE_DISCOVER&baleId="+f.bale,409); }
        finally { f.jdbc.update("UPDATE temporary_command_receipt SET signed_receipt=? WHERE id=?",signature,row.get("id")); }
    }
    static void evidence(QuarantineHttpFixture f,ObjectNode command) throws Exception {
        for(String time:java.util.List.of(Instant.now().plusSeconds(3600).toString(),Instant.now().minusSeconds(4*86400).toString())) {
            var changed=command.deepCopy(); ((ObjectNode)changed.path("evidence")).put("observedAt",time); f.maker.command(changed,409);
        }
        var wrong=command.deepCopy(); ((ObjectNode)wrong.at("/evidence/attachments/0")).put("evidenceSha256","0".repeat(64)); f.maker.command(wrong,400);
        var missing=command.deepCopy(); ((ObjectNode)missing.path("evidence")).putArray("attachments"); f.maker.command(missing,400);
        f.checker.read(f.warehouse,f.attachment.path("attachmentId").asText(),true,404);
    }
    static void approval(QuarantineHttpFixture f,ObjectNode command) throws Exception {
        permission(f,false); f.checker.command(command,403); permission(f,true);
        f.jdbc.update("UPDATE inventory_position SET upstream_present=false WHERE bale_id=UUID_TO_BIN(?)",f.bale);
        try { f.checker.command(f.command("APPROVE",f.checker),409); }
        finally { f.jdbc.update("UPDATE inventory_position SET upstream_present=true WHERE bale_id=UUID_TO_BIN(?)",f.bale); }
        f.jdbc.update("UPDATE bale_commitment SET dispatch_cycle=dispatch_cycle+1 WHERE bale_id=UUID_TO_BIN(?)",f.bale);
        try { f.checker.command(f.command("APPROVE",f.checker),409); }
        finally { f.jdbc.update("UPDATE bale_commitment SET dispatch_cycle=dispatch_cycle-1 WHERE bale_id=UUID_TO_BIN(?)",f.bale); }
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM quarantine_release",Integer.class)).isZero();
    }
    private static void permission(QuarantineHttpFixture f,boolean grant) {
        if(grant) f.jdbc.update("INSERT INTO role_permission SELECT ur.role_id,p.id FROM user_role ur JOIN app_user u ON u.id=ur.user_id CROSS JOIN permission p WHERE u.username=? AND p.code='correction:approve'",f.checkerUser);
        else f.jdbc.update("DELETE rp FROM role_permission rp JOIN user_role ur ON ur.role_id=rp.role_id JOIN app_user u ON u.id=ur.user_id JOIN permission p ON p.id=rp.permission_id WHERE u.username=? AND p.code='correction:approve'",f.checkerUser);
    }
}
