package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

final class TemporaryConfirmationEffectChecks {
    private final WebApplicationContext context;
    TemporaryConfirmationEffectChecks(WebApplicationContext context) { this.context=context; }
    void verify() throws Exception { approval();success(); }
    private void approval() throws Exception {
        var f=new TemporaryConfirmationFixture(context,true);var actor=f.supervisor();
        var command=f.command("SHORT_APPROVE",actor);byte[] original=f.execute(command,actor);
        assertThat(f.read(command,actor).currentEffect().path("evaluation").asText()).isEqualTo("CURRENT_CONTEXT_MATCHES");
        f.only(actor,List.of("temporary_order:confirm"));
        f.jdbc.update("UPDATE outbound_order SET created_at=UTC_TIMESTAMP(3) WHERE id=UUID_TO_BIN(?)",f.source.f.order);
        f.jdbc.update("UPDATE device SET temporary_order_enabled=false WHERE id=UUID_TO_BIN(?)",f.source.a.device());
        var view=f.read(command,actor);var effect=view.currentEffect();
        assertThat(effect.path("evaluation").asText()).isEqualTo("CONTEXT_CHANGED");
        assertThat(effect.path("reasons").toString()).contains("EXECUTION_PERMISSION_MISSING","CANDIDATE_CONTEXT_CHANGED","TEMPORARY_ADMISSION_DISABLED");
        assertThat(view.originalCommandReceiptUtf8().getBytes(StandardCharsets.UTF_8)).isEqualTo(original);
        assertThat(f.execute(command,actor)).isEqualTo(original);assertThat(effect.path("applicable").asBoolean()).isFalse();
        effect.path("capabilities").forEach(value->assertThat(value.asBoolean()).isFalse());
    }
    private void success() throws Exception {
        var f=new TemporaryConfirmationFixture(context,false);var actor=f.operator();
        var command=f.command("NORMAL_CONFIRM",actor);var original=f.execute(command,actor);
        f.jdbc.update("UPDATE server_state SET recovery_epoch=2,service_mode='RECOVERY_PENDING' WHERE id=1");
        f.jdbc.update("UPDATE device SET temporary_order_enabled=false WHERE id=UUID_TO_BIN(?)",f.source.a.device());
        try {
            var view=f.read(command,actor);
            assertThat(view.currentEffect().path("evaluation").asText()).isEqualTo("COMMITTED");
            assertThat(view.currentEffect().path("reasons").toString()).contains("RECOVERY_EPOCH_CHANGED","ORIGINAL_SUCCESS_COMMITTED","TEMPORARY_ADMISSION_DISABLED");
            assertThat(f.execute(command,actor)).isEqualTo(original);
            assertThat(SubmissionJson.parse(view.originalCommandReceiptUtf8()).at("/businessDecision/submissionReceipt/status").asText()).isEqualTo("SUCCEEDED");
        } finally { f.jdbc.update("UPDATE server_state SET recovery_epoch=1,service_mode='NORMAL' WHERE id=1"); }
    }
}
