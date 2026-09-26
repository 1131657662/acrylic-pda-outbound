package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.command.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;
import static com.acrylic.outbound.inventory.TemporaryConfirmationChecks.reject;

final class TemporaryConfirmationLedgerChecks {
    private final WebApplicationContext context;
    TemporaryConfirmationLedgerChecks(WebApplicationContext context) { this.context=context; }
    void verify() throws Exception { boundary();synthetic();for(String kind:List.of("hash","signature","event","audit","result"))corrupt(kind); }
    private void boundary() throws Exception {
        var f=new TemporaryConfirmationFixture(context,false);var actor=f.operator();var command=f.command("NORMAL_CONFIRM",actor);var before=f.state();
        reject("COMMAND_EXECUTION_NOT_IMPLEMENTED",()->f.execute(f.bean(TemporaryCommandService.class),command,actor));
        assertThat(f.state()).isEqualTo(before);
        var mappings=f.context.getBean("requestMappingHandlerMapping",org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class);
        mappings.getHandlerMethods().forEach((mapping,method)->{
            if(method.getBeanType()==CommandReadController.class)assertThat(mapping.getMethodsCondition().getMethods())
                .containsExactly(org.springframework.web.bind.annotation.RequestMethod.GET);
        });
    }
    private void synthetic() throws Exception {
        var f=new TemporaryConfirmationFixture(context,false);var actor=f.operator();var command=f.command("NORMAL_CONFIRM",actor);
        ((com.fasterxml.jackson.databind.node.ObjectNode)command.get("expected")).putNull("candidateContextSha256");
        CommandOperation operation=new CommandOperation() {
            public String commandType() { return "NORMAL_CONFIRM"; }
            public void apply(CommandContext trusted) { }
        };
        var service=f.assemble(new TemporaryConfirmationFixture.Assembly(f.bean(CommandReceiptCodec.class),f.bean(com.acrylic.outbound.audit.AuditWriter.class),
            f.bean(CommandLocks.class),f.bean(TemporaryConfirmationSource.class),f.bean(SubmissionAccounting.class)),List.of(operation));
        var original=f.execute(service,command,actor);assertThat(SubmissionJson.parse(original).path("status").asText()).isEqualTo("INTERNAL_TEST_ONLY");
        assertThat(f.execute(command,actor)).isEqualTo(original);
        assertThat(f.read(command,actor).currentEffect().path("evaluation").asText()).isEqualTo("UNKNOWN");
        assertThat(f.jdbc.queryForObject("SELECT status FROM submission WHERE id=UUID_TO_BIN(?)",String.class,f.source.id())).isEqualTo("REVIEW_REQUIRED");
        f.execute(f.command("NORMAL_CONFIRM",actor),actor);
        assertThat(f.execute(service,command,actor)).isEqualTo(original);
    }
    private void corrupt(String kind) throws Exception {
        var f=new TemporaryConfirmationFixture(context,false);var actor=f.operator();var command=f.command("NORMAL_CONFIRM",actor);
        var original=f.execute(command,actor);String receipt=SubmissionJson.parse(original).path("receiptId").asText();
        switch(kind) {
            case "hash"->f.jdbc.update("UPDATE temporary_command_receipt SET original_response_sha256=? WHERE id=UUID_TO_BIN(?)","0".repeat(64),receipt);
            case "signature"->f.jdbc.update("UPDATE temporary_command_receipt SET signature_key_id='unknown-test-key' WHERE id=UUID_TO_BIN(?)",receipt);
            case "event"->f.jdbc.update("UPDATE temporary_decision_event SET context_bytes=? WHERE receipt_id=UUID_TO_BIN(?)","{}".getBytes(StandardCharsets.UTF_8),receipt);
            case "audit"->f.jdbc.update("UPDATE operation_event SET event_type='DAMAGED_TEST' WHERE entity_type='TEMPORARY_COMMAND' AND entity_id=UUID_TO_BIN(?)",receipt);
            case "result"->f.jdbc.update("UPDATE order_result_version SET actual_count=actual_count+1 WHERE submission_id=UUID_TO_BIN(?)",f.source.id());
            default->throw new IllegalArgumentException();
        }
        reject("COMMAND_RECEIPT_INTEGRITY_FAILURE",()->f.execute(command,actor));
        reject("COMMAND_RECEIPT_INTEGRITY_FAILURE",()->f.read(command,actor));
        assertThat(f.jdbc.queryForObject("SELECT original_response_bytes FROM temporary_command_receipt WHERE id=UUID_TO_BIN(?)",byte[].class,receipt)).isEqualTo(original);
    }
}
