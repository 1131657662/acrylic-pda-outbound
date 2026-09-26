package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.command.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

public final class TemporaryConfirmationChecks {
    private final WebApplicationContext context;
    public TemporaryConfirmationChecks(WebApplicationContext context) { this.context=context; }
    public void verify() throws Exception {
        assertThat(context.getBeansOfType(CommandOperation.class)).isEmpty();
        normal();shortShipment();
        new TemporaryConfirmationFailureChecks(context).verify();
        new TemporaryConfirmationEffectChecks(context).verify();
        new TemporaryConfirmationEligibilityChecks(context).verify();
        new TemporaryConfirmationLedgerChecks(context).verify();
        new TemporaryConfirmationConcurrencyChecks(context).verify();
    }
    private void normal() throws Exception {
        var f=new TemporaryConfirmationFixture(context,false);var actor=f.operator();
        var command=f.command("NORMAL_CONFIRM",actor);byte[] original=f.execute(command,actor);
        var receipt=SubmissionJson.parse(original);
        assertThat(receipt.path("status").asText()).isEqualTo("RECORDED");
        assertThat(receipt.path("businessDecision").path("kind").asText()).isEqualTo("NORMAL_CONFIRM");
        assertThat(f.execute(command,actor)).isEqualTo(original);
        var view=f.transport.http(f.source,"/pda/temporary-command-receipts/"+command.path("commandId").asText()
            +"?warehouseId="+f.source.f.web.identity().warehouse(),null);
        assertThat(view.path("originalCommandReceiptUtf8").asText().getBytes(java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(original);
        assertThat(view.at("/currentEffect/evaluation").asText()).isEqualTo("COMMITTED");
        assertThat(f.transport.http(f.source,"/pda/submissions/"+f.source.id(),null))
            .isEqualTo(receipt.path("businessDecision").path("submissionReceipt"));
        var changed=command.deepCopy().put("reason","changed");reject("IDEMPOTENCY_CONTENT_MISMATCH",()->f.execute(changed,actor));
        TemporaryConfirmationFixture.authenticate(actor);
        try { assertThat(f.bean(CommandReadService.class).original(f.source.f.web.identity().warehouse(),command.path("commandId").asText())).isEqualTo(original); }
        finally { SecurityContextHolder.clearContext(); }
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_record WHERE submission_id=UUID_TO_BIN(?)",Integer.class,f.source.id())).isEqualTo(2);
    }
    private void shortShipment() throws Exception {
        var f=new TemporaryConfirmationFixture(context,true);var operator=f.operator();var supervisor=f.supervisor();
        var confirm=f.command("NORMAL_CONFIRM",operator);
        reject("SHORT_SHIPMENT_APPROVAL_REQUIRED",()->f.execute(confirm,operator));
        reject("SHORT_SHIPMENT_SELF_APPROVAL_FORBIDDEN",()->f.execute(f.command("SHORT_APPROVE",operator),operator));
        var approval=f.command("SHORT_APPROVE",supervisor);var approved=f.execute(approval,supervisor);
        assertThat(SubmissionJson.parse(approved).path("businessDecision").path("submissionReceipt").isNull()).isTrue();
        assertThat(f.jdbc.queryForObject("SELECT status FROM submission WHERE id=UUID_TO_BIN(?)",String.class,f.source.id())).isEqualTo("REVIEW_REQUIRED");
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_record WHERE submission_id=UUID_TO_BIN(?)",Integer.class,f.source.id())).isZero();
        var result=f.execute(f.command("NORMAL_CONFIRM",operator),operator);
        assertThat(SubmissionJson.parse(result).path("businessDecision").path("shortApprovalReceiptId"))
            .isEqualTo(SubmissionJson.parse(approved).path("receiptId"));
        assertThat(f.execute(approval,supervisor)).isEqualTo(approved);
    }
    static void reject(String code,org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(BusinessException.class)
            .extracting(error->((BusinessException)error).code()).isEqualTo(code);
    }
}
