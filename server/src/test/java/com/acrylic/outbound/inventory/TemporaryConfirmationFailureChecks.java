package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.temporary.command.*;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;
import static com.acrylic.outbound.inventory.TemporaryConfirmationChecks.reject;

final class TemporaryConfirmationFailureChecks {
    private final WebApplicationContext context;
    TemporaryConfirmationFailureChecks(WebApplicationContext context) { this.context=context; }
    void verify() throws Exception { candidates();permissions();signingFailure();auditFailure();successSigningFailure(); }
    private void candidates() throws Exception {
        var f=new TemporaryConfirmationFixture(context,false);var actor=f.operator();var command=f.command("NORMAL_CONFIRM",actor);
        f.jdbc.update("UPDATE outbound_order SET created_at=UTC_TIMESTAMP(3) WHERE id=UUID_TO_BIN(?)",f.source.f.order);
        var before=f.state();reject("COMMAND_CONTEXT_CHANGED",()->f.execute(command,actor));assertThat(f.state()).isEqualTo(before);
        reject("TEMPORARY_DUPLICATES_UNRESOLVED",()->f.execute(f.command("NORMAL_CONFIRM",actor),actor));assertThat(f.state()).isEqualTo(before);
    }
    private void permissions() throws Exception {
        var f=new TemporaryConfirmationFixture(context,true);var actor=f.supervisor();
        f.only(actor,List.of("submission:review"));var before=f.state();
        reject("PERMISSION_DENIED",()->f.execute(f.command("NORMAL_CONFIRM",actor),actor));assertThat(f.state()).isEqualTo(before);
        var approval=f.command("SHORT_APPROVE",actor);var original=f.execute(approval,actor);
        f.only(actor,List.of("temporary_order:confirm"));
        assertThat(f.execute(approval,actor)).isEqualTo(original);
        reject("SHORT_SHIPMENT_APPROVAL_REQUIRED",()->f.execute(f.command("NORMAL_CONFIRM",f.operator()),f.operator()));
        reject("PERMISSION_DENIED",()->f.execute(f.command("SHORT_APPROVE",actor),actor));
    }
    private void signingFailure() throws Exception {
        var f=new TemporaryConfirmationFixture(context,false);var actor=f.operator();var command=f.command("NORMAL_CONFIRM",actor);
        var codec=new CommandReceiptCodec(new OfflineSigner("","",f.bean(JsonText.class)));
        var service=f.service(codec,f.bean(com.acrylic.outbound.audit.AuditWriter.class));var before=f.state();
        TemporaryConfirmationFixture.authenticate(actor);
        try { reject("SIGNING_KEY_UNAVAILABLE",()->service.execute(TemporaryConfirmationFixture.bytes(command),"sign-failure")); }
        finally { SecurityContextHolder.clearContext(); }
        assertThat(f.state()).isEqualTo(before);f.execute(command,actor);
    }
    private void auditFailure() throws Exception {
        var f=new TemporaryConfirmationFixture(context,false);var actor=f.operator();var command=f.command("NORMAL_CONFIRM",actor);
        var audit=new com.acrylic.outbound.audit.AuditWriter(f.jdbc) {
            @Override public void append(Event event,String requestId) {
                // Real FK failure at the final command-audit write, after accounting, command receipt and event.
                super.append(new Event(UUID.randomUUID(),event.type(),event.entityType(),event.entity(),event.json()),requestId);
            }
        };
        var service=f.service(f.bean(CommandReceiptCodec.class),audit);var before=f.state();
        TemporaryConfirmationFixture.authenticate(actor);
        try { assertThatThrownBy(()->service.execute(TemporaryConfirmationFixture.bytes(command),"audit-failure"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class).hasMessageContaining("operation_event"); }
        finally { SecurityContextHolder.clearContext(); }
        assertThat(f.state()).isEqualTo(before);f.execute(command,actor);
    }
    private void successSigningFailure() throws Exception {
        var f=new TemporaryConfirmationFixture(context,false);var actor=f.operator();var command=f.command("NORMAL_CONFIRM",actor);
        var unavailable=new OfflineSigner("","",f.bean(JsonText.class));
        var accounting=new com.acrylic.outbound.submissions.SubmissionAccounting(f.jdbc,
            new com.acrylic.outbound.submissions.SubmissionReceipts(unavailable,f.jdbc),
            f.bean(com.acrylic.outbound.submissions.SubmissionStore.class),f.bean(com.acrylic.outbound.submissions.SubmissionEvents.class));
        var service=f.service(f.bean(CommandReceiptCodec.class),f.bean(com.acrylic.outbound.audit.AuditWriter.class),accounting);
        var before=f.state();TemporaryConfirmationFixture.authenticate(actor);
        try { reject("SIGNING_KEY_UNAVAILABLE",()->service.execute(TemporaryConfirmationFixture.bytes(command),"success-sign-failure")); }
        finally { SecurityContextHolder.clearContext(); }
        assertThat(f.state()).isEqualTo(before);f.execute(command,actor);
    }
}
