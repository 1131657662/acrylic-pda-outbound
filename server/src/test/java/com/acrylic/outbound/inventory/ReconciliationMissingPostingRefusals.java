package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Isolated drift, invalid evidence and final SQL fault must preserve business facts. */
final class ReconciliationMissingPostingRefusals {
    private final WebApplicationContext context;
    private final Path attempt;
    ReconciliationMissingPostingRefusals(WebApplicationContext context, Path attempt) { this.context = context; this.attempt = attempt; }
    void verify() throws Exception { badEvidence(); drift(); finalSql(); }

    private void badEvidence() throws Exception {
        var f = new ReconciliationMissingPostingFixture(context, attempt, "bad-evidence"); f.discover(); var before = f.state();
        var body = f.command("RECONCILE_SUBMIT", false);
        ((ObjectNode) body.at("/evidence/attachments/0")).put("evidenceSha256", "0".repeat(64));
        f.maker.command(body, 400); assertThat(f.state()).isEqualTo(before);
        var coerced = f.command("RECONCILE_SUBMIT", false);
        var attachment = (ObjectNode) coerced.at("/evidence/attachments/0");
        attachment.put("sizeBytes", attachment.path("sizeBytes").asLong());
        f.maker.command(coerced, 400); assertThat(f.state()).isEqualTo(before);
        var old = f.base("RECONCILE_ADVANCE").put("caseId", f.caseId).put("conclusion", "Cannot bypass evidence");
        old.set("expected", SubmissionJson.object().put("caseId", f.caseId).put("status", "OPEN").put("rowVersion", "1").put("userAuthVersion", "1"));
        f.maker.command(old, 409); assertThat(f.state()).isEqualTo(before);
        f.submit(); var approved = f.command("RECONCILE_APPROVE", true);
        var rejected = f.maker.command(approved, 403);
        Files.write(f.directory.resolve("maker-cannot-approve.json"), rejected);
    }

    private void drift() throws Exception {
        var f = new ReconciliationMissingPostingFixture(context, attempt, "drift"); f.discover(); f.submit();
        var approval = f.command("RECONCILE_APPROVE", true);
        f.original.approve(1, f.original.originalDispatchWeight + 10); var changed = f.state();
        f.checker.command(approval, 409); assertThat(f.state()).isEqualTo(changed);
        f.checker.command(f.command("RECONCILE_APPROVE", true), 409); assertThat(f.state()).isEqualTo(changed);
    }

    private void finalSql() throws Exception {
        var f = new ReconciliationMissingPostingFixture(context, attempt, "sql-rollback"); f.discover(); f.submit();
        var approval = f.command("RECONCILE_APPROVE", true); var before = f.state();
        f.original.jdbc.execute("CREATE TRIGGER reconcile_fail_posting BEFORE INSERT ON reconciliation_posting FOR EACH ROW"
            + " SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ISOLATED final posting reference failure'");
        try { f.checker.command(approval, 500); }
        finally { f.original.jdbc.execute("DROP TRIGGER reconcile_fail_posting"); }
        assertThat(f.state()).isEqualTo(before);
        f.signed(f.checker, approval);
    }
}
