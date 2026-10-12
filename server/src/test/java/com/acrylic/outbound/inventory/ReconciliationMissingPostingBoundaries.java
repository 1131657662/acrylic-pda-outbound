package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual Session/CSRF refusals; injected stock drift is a fixture fault, not claimed product mutation. */
final class ReconciliationMissingPostingBoundaries {
    private final WebApplicationContext context;
    private final Path attempt;
    ReconciliationMissingPostingBoundaries(WebApplicationContext context, Path attempt) { this.context=context; this.attempt=attempt; }
    void verify() throws Exception { identity(); revokedApproval(); stockDrift(); browserInput(); }

    private void revokedApproval() throws Exception {
        var f = new ReconciliationMissingPostingFixture(context, attempt, "revoked-approval"); f.discover(); f.submit();
        var approval = f.command("RECONCILE_APPROVE", true); var before = f.state();
        String actor = f.original.jdbc.queryForObject("SELECT BIN_TO_UUID(u.id) FROM app_user u JOIN user_role ur ON ur.user_id=u.id"
            + " JOIN role r ON r.id=ur.role_id JOIN user_warehouse uw ON uw.user_id=u.id WHERE r.code='SUPERVISOR'"
            + " AND uw.warehouse_id=UUID_TO_BIN(?) ORDER BY u.id DESC LIMIT 1", String.class, f.original.warehouse);
        f.original.jdbc.update("DELETE FROM user_role WHERE user_id=UUID_TO_BIN(?)", actor);
        f.checker.command(approval, 403); assertThat(f.state()).isEqualTo(before);
        f.original.jdbc.update("UPDATE app_user SET auth_version=auth_version+1 WHERE id=UUID_TO_BIN(?)", actor);
        f.checker.command(approval, 401); assertThat(f.state()).isEqualTo(before);
    }

    private void identity() throws Exception {
        var f=new ReconciliationMissingPostingFixture(context,attempt,"identity"); f.discover(); var before=f.state();
        f.maker.command(f.command("RECONCILE_DISCOVER",false),409); assertThat(f.state()).isEqualTo(before);
        var altered=f.command("RECONCILE_SUBMIT",false); altered.put("unexpected","forged");
        f.maker.command(altered,400); assertThat(f.state()).isEqualTo(before);
        f.original.jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='SUPERVISOR'",Ids.bytes(f.original.makerIdentity.user().id()));
        f.submit(); before=f.state(); f.maker.command(f.command("RECONCILE_APPROVE",true),403); assertThat(f.state()).isEqualTo(before);
        var other=IdentityFixture.create(f.original.jdbc,context.getBean(PasswordEncoder.class),"SUPERVISOR");
        var outsider=new CorrectionAttachmentHttp(other.user().username(),f.directory.resolve("outsider-http.log"));
        outsider.get("/corrections/reconciliation/context?warehouseId="+f.original.warehouse+"&commandType=RECONCILE_APPROVE&caseId="+f.caseId,404);
        outsider.command(f.command("RECONCILE_APPROVE",true),404); assertThat(f.state()).isEqualTo(before);
        byte[] receipt=f.signed(f.checker,f.command("RECONCILE_APPROVE",true));
        var command=SubmissionJson.parse(Files.readString(f.directory.resolve(SubmissionJson.parse(receipt).path("commandId").asText()+"-command.json")));
        ((com.fasterxml.jackson.databind.node.ObjectNode)command).put("reason","different original intent");
        f.checker.command(command,409);
        byte[] read=f.checker.get("/corrections/commands/"+command.path("commandId").asText()+"?warehouseId="+f.original.warehouse+"&commandType=RECONCILE_APPROVE",200);
        assertThat(read).containsExactly(receipt); Files.write(f.directory.resolve("original-read.json"),read);
    }

    private void stockDrift() throws Exception {
        for(String name:new String[]{"upstream","blocked","owner","cycle"}) {
            var f=new ReconciliationMissingPostingFixture(context,attempt,"drift-"+name);f.discover();f.submit();
            var approval=f.command("RECONCILE_APPROVE",true);String bale=f.original.facts(f.original.dispatch).path("baleId").asText();
            String sql=switch(name) {
                case "upstream" -> "UPDATE inventory_position SET upstream_present=NOT upstream_present,row_version=row_version+1 WHERE bale_id=UUID_TO_BIN(?)";
                case "blocked" -> "UPDATE inventory_position SET blocked_reason='ISOLATED_CHANGED',row_version=row_version+1 WHERE bale_id=UUID_TO_BIN(?)";
                case "owner" -> "UPDATE bale_commitment SET state='REVIEW_RESERVED' WHERE bale_id=UUID_TO_BIN(?)";
                default -> "UPDATE bale SET dispatch_cycle=dispatch_cycle+1 WHERE id=UUID_TO_BIN(?)";
            };
            f.original.jdbc.update(sql,bale);var changed=f.state();f.checker.command(approval,409);assertThat(f.state()).isEqualTo(changed);
            f.checker.command(f.command("RECONCILE_APPROVE",true),409);assertThat(f.state()).isEqualTo(changed);
        }
    }

    private void browserInput() throws Exception {
        var f=new ReconciliationMissingPostingFixture(context,attempt,"browser");
        String checker=f.original.jdbc.queryForObject("SELECT username FROM app_user u JOIN user_role ur ON ur.user_id=u.id"
            +" JOIN role r ON r.id=ur.role_id JOIN user_warehouse uw ON uw.user_id=u.id WHERE r.code='SUPERVISOR'"
            +" AND uw.warehouse_id=UUID_TO_BIN(?) ORDER BY u.id DESC LIMIT 1",String.class,f.original.warehouse);
        var input=SubmissionJson.object().put("warehouse",f.original.warehouse).put("order",f.original.order).put("dispatch",f.original.dispatch);
        input.putObject("maker").put("username",f.original.makerIdentity.user().username()).put("password","Test-password-123");
        input.putObject("checker").put("username",checker).put("password","Test-password-123");
        Files.writeString(f.directory.resolve("browser-input.json"),SubmissionJson.canonical(input));
        Files.write(f.directory.resolve("list-original.json"),f.maker.get("/corrections/reconciliation?warehouseId="+f.original.warehouse,200));
    }
}
