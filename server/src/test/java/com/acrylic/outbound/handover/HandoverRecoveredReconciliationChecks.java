package com.acrylic.outbound.handover;

import com.acrylic.outbound.auth.*;
import com.acrylic.outbound.recovery.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Real restored source and new fence, with synthetic supervisors clearly separated from original facts. */
public final class HandoverRecoveredReconciliationChecks {
    public static void verify(WebApplicationContext app,Path attempt,HandoverRecoveredTemporaryBinding binding,
            RecoveryReleaseReconciliation.Request input) throws Exception {
        var jdbc=app.getBean(JdbcTemplate.class); var directory=attempt.resolve("recovered-fault");
        byte[] command=Files.readAllBytes(directory.resolve("fault-command.json"));
        var receipt=SubmissionJson.parse(Files.readAllBytes(directory.resolve("fault-receipt.json")));
        String username=jdbc.queryForObject("SELECT username FROM app_user WHERE id=UUID_TO_BIN(?)",String.class,
            receipt.path("actor").path("principalId").asText());
        var actor=app.getBean(IdentityStore.class).loadUserByUsername(username);
        var review=app.getBean(HandoverRecoveredTemporaryReview.class);
        var snapshots=new com.acrylic.outbound.e2averify.E2aMigrationSnapshot(jdbc,attempt,attempt.resolve("storage"));
        var before=snapshots.capture(); HandoverRecoveredTemporaryFixture.authenticate(actor);
        try {
            var proof=review.reconcile(command,binding,input);
            assertThat(proof.facts().path("originalFactsPreserved").asBoolean()).isTrue();
            assertThat(proof.releaseAllowed()).isFalse(); assertThat(proof.ordinaryWritesAllowed()).isFalse();
            assertThat(proof.localDeletionAllowed()).isFalse(); assertThat(proof.gaps()).contains("PHYSICAL_REVIEW_REQUIRED");
            assertThat(review.reconcile(command,binding,input).sha256()).isEqualTo(proof.sha256());
            var inventory=review.inspect(command,binding);
            var original=SubmissionJson.parse(Files.readAllBytes(input.history().end().file())).path("tables");
            int negatives=RecoveryReceivedScopeSemanticChecks.verify(original,before.path("tables"),inventory,input.targetKeys());
            HandoverRecoveredReconciliationTailChecks.verify(app,binding,new HandoverRecoveredReconciliationTailChecks.Input(
                command,Files.readAllBytes(directory.resolve("fault-receipt.json")),input,snapshots));
            Files.writeString(attempt.resolve("release-reconciliation.json"),SubmissionJson.canonical(proof.facts())+"\n");
            Files.writeString(attempt.resolve("release-reconciliation.sha256"),proof.sha256());
            Files.writeString(attempt.resolve("release-reconciliation-checks.json"),SubmissionJson.object().put("semanticNegativeChecks",negatives)
                .put("crossWarehouseTailRollbacks",2).put("readOnly",true).toPrettyString());
            assertThat(snapshots.capture()).isEqualTo(before);
        } finally { SecurityContextHolder.clearContext(); }
    }
    private HandoverRecoveredReconciliationChecks() { }
}
