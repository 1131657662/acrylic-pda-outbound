package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

final class ReturnedStockRefusalChecks {
    private final WebApplicationContext context; private final Path attempt;
    ReturnedStockRefusalChecks(WebApplicationContext context, Path attempt) { this.context=context; this.attempt=attempt; }
    void verify() throws Exception {
        blockers(); staleApproval(); selfApproval(); badEvidence(); crossWarehouse(); rollback();
    }
    private void blockers() throws Exception {
        var f = new ReturnedStockHttpFixture(context, attempt, "blockers");
        f.original.jdbc.update("UPDATE inventory_position SET blocked_reason='ISOLATED_QUARANTINE' WHERE bale_id=UUID_TO_BIN(?)", f.bale);
        var command = f.command("DISCOVER", false); var before = f.state();
        assertThat(SubmissionJson.parse(f.maker.command(command, 409)).path("code").asText()).isEqualTo("CORRECTION_LEDGER_CONFLICT");
        assertThat(f.state()).isEqualTo(before);
        f.original.jdbc.update("UPDATE inventory_position SET blocked_reason=NULL,upstream_present=0 WHERE bale_id=UUID_TO_BIN(?)", f.bale);
        command = f.command("DISCOVER", false); before = f.state(); f.maker.command(command, 409); assertThat(f.state()).isEqualTo(before);
    }
    private void staleApproval() throws Exception {
        var f = new ReturnedStockHttpFixture(context, attempt, "stale"); f.discover(); f.submit();
        var command = f.command("APPROVE", true);
        f.original.jdbc.update("UPDATE inventory_position SET row_version=row_version+1 WHERE bale_id=UUID_TO_BIN(?)", f.bale);
        var before = f.state(); f.checker.command(command, 409); assertThat(f.state()).isEqualTo(before);
        f.checker.command(f.command("APPROVE", true), 409); assertThat(f.state()).isEqualTo(before);
    }
    private void selfApproval() throws Exception {
        var f = new ReturnedStockHttpFixture(context, attempt, "self"); f.discover(); f.submit();
        var command = f.command("APPROVE", true); var before = f.state(); f.maker.command(command, 403); assertThat(f.state()).isEqualTo(before);
        f.original.jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='SUPERVISOR'", Ids.bytes(f.original.makerIdentity.user().id()));
        var privilegedMaker = new CorrectionAttachmentHttp(f.original.makerIdentity.user().username(), f.original.evidence.resolve("maker-supervisor-http.log"));
        var refusal = SubmissionJson.parse(privilegedMaker.command(command, 403));
        assertThat(refusal.path("code").asText()).isEqualTo("CORRECTION_SELF_APPROVAL_DENIED");
        assertThat(f.state()).isEqualTo(before);
    }

    private void badEvidence() throws Exception {
        var f = new ReturnedStockHttpFixture(context, attempt, "bad-evidence"); f.discover(); var before = f.state();
        var command = f.command("SUBMIT", false);
        ((ObjectNode) command.at("/evidence/attachments/0")).put("evidenceSha256", "0".repeat(64));
        f.maker.command(command, 400); assertThat(f.state()).isEqualTo(before);
        var legacy = SubmissionJson.object().put("commandVersion",1).put("commandId",java.util.UUID.randomUUID().toString())
            .put("commandType","RECONCILE_ADVANCE").put("warehouseId",f.original.warehouse).put("transportRecoveryEpoch","1")
            .put("reason","ISOLATED bypass must fail").put("caseId",f.caseId).put("conclusion","cannot bypass evidence");
        legacy.set("expected", SubmissionJson.object().put("caseId",f.caseId).put("status","OPEN").put("rowVersion","1").put("userAuthVersion","1"));
        assertThat(SubmissionJson.parse(f.maker.command(legacy,409)).path("code").asText()).isEqualTo("RECONCILIATION_EVIDENCE_REQUIRED");
        assertThat(f.state()).isEqualTo(before);
        f.submit();
    }

    private void crossWarehouse() throws Exception {
        var f = new ReturnedStockHttpFixture(context, attempt, "cross-warehouse"); f.discover(); f.submit();
        var command = f.command("APPROVE", true); var before = f.state();
        var stranger = IdentityFixture.create(f.original.jdbc, context.getBean(org.springframework.security.crypto.password.PasswordEncoder.class), "SUPERVISOR");
        var wire = new CorrectionAttachmentHttp(stranger.user().username(), f.original.evidence.resolve("cross-warehouse-http.log"));
        var crossResponse = SubmissionJson.parse(wire.command(command,404));
        assertThat(crossResponse.path("code").asText()).isEqualTo("CORRECTION_DOCUMENT_NOT_FOUND");
        assertThat(f.state()).isEqualTo(before);
        String checker = f.original.jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM app_user WHERE username=(SELECT username FROM app_user u"
            + " JOIN user_role ur ON ur.user_id=u.id JOIN role r ON r.id=ur.role_id JOIN user_warehouse uw ON uw.user_id=u.id"
            + " WHERE r.code='SUPERVISOR' AND uw.warehouse_id=UUID_TO_BIN(?) ORDER BY u.id DESC LIMIT 1)", String.class, f.original.warehouse);
        f.original.jdbc.update("DELETE ur FROM user_role ur JOIN role r ON r.id=ur.role_id WHERE ur.user_id=UUID_TO_BIN(?) AND r.code='SUPERVISOR'",checker);
        try { f.checker.command(command,403); assertThat(f.state()).isEqualTo(before); }
        finally { f.original.jdbc.update("INSERT INTO user_role SELECT UUID_TO_BIN(?),id FROM role WHERE code='SUPERVISOR'",checker); }
        f.signed(f.checker,command);
    }
    private void rollback() throws Exception {
        var f = new ReturnedStockHttpFixture(context, attempt, "rollback"); f.discover(); f.submit(); var before = f.state();
        f.original.jdbc.execute("CREATE TRIGGER returned_release_test_failure BEFORE INSERT ON returned_stock_release FOR EACH ROW"
            + " SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ISOLATED last SQL failure'");
        var command = f.command("APPROVE", true);
        try { f.checker.command(command, 500); assertThat(f.state()).isEqualTo(before); }
        finally { f.original.jdbc.execute("DROP TRIGGER returned_release_test_failure"); }
        assertThat(f.original.jdbc.queryForObject("SELECT COUNT(*) FROM correction_command_receipt WHERE original_key=UUID_TO_BIN(?)",
            Integer.class, command.path("commandId").asText())).isZero();
        f.signed(f.checker, command);
    }
}
