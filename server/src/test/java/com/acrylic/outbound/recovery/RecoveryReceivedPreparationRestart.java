package com.acrylic.outbound.recovery;

import com.acrylic.outbound.auth.IdentityStore;
import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.handover.HandoverRecoveredTemporaryPreparation;
import com.acrylic.outbound.handover.HandoverRecoveredPhysicalReview;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Retained pins and original PREPARE bytes only; no new identity, request or signature. */
public final class RecoveryReceivedPreparationRestart {
    public static void verify(WebApplicationContext app,Path attempt,Path original) throws Exception {
        var jdbc=app.getBean(JdbcTemplate.class); var input=reload(jdbc,attempt,original);
        assertThat(input.facts().path("sourcePid").asLong()).isNotEqualTo(ProcessHandle.current().pid());
        var actor=app.getBean(IdentityStore.class).loadUserByUsername(Files.readString(attempt.resolve("preparation-supervisor.txt")));
        var snapshots=new E2aMigrationSnapshot(jdbc,attempt,attempt.resolve("storage")); var before=snapshots.capture();
        byte[] command=Files.readAllBytes(attempt.resolve("preparation-command.json"));
        byte[] receipt=Files.readAllBytes(attempt.resolve("preparation-receipt.json"));
        RecoveryRecipientFixture.authenticate(actor);
        try {
            assertThat(app.getBean(HandoverRecoveredTemporaryPreparation.class).prepare(command,"prepare-restart",
                input.qualification(),input.request())).isEqualTo(receipt);
            assertThat(snapshots.capture()).isEqualTo(before);
        } finally { SecurityContextHolder.clearContext(); }
        Files.write(attempt.resolve("preparation-restart-result.json"),RecoveryControlJson.encode(SubmissionJson.object()
            .put("pid",ProcessHandle.current().pid()).put("sourcePid",input.facts().path("sourcePid").asLong()).put("originalPinsReloaded",true)
            .put("newSignatureIssued",false).put("databaseUnchanged",true).put("originalReceiptSha256",sha256(receipt))
            .put("ordinaryWritesAllowed",false).put("localDeletionAllowed",false).put("mayResumeScanning",false)));
    }
    private static Reloaded reload(JdbcTemplate jdbc,Path attempt,Path original) throws Exception {
        byte[] input=Files.readAllBytes(attempt.resolve("qualification-restart-input.json"));
        assertThat(sha256(input)).isEqualTo(Files.readString(attempt.resolve("qualification-restart-input.sha256")));
        var facts=readCanonical(input); var request=RecoveryReceivedScopeReleaseReload.request(jdbc,attempt,original);
        byte[] bytes=Files.readAllBytes(attempt.resolve("scope-restart-input.json"));
        assertThat(sha256(bytes)).isEqualTo(Files.readString(attempt.resolve("scope-restart-input.sha256"))); var scope=readCanonical(bytes);
        var authority=new RecoveryOperationsAuthority(request.reconciliation().authority(),Path.of(text(facts,"identityFile")),text(facts,"identitySha256"));
        var approval=new RecoveryOperationsApproval.Request(authority,Path.of(text(scope,"approvalFile")),text(scope,"approvalSha256"));
        var recipient=RecoveryRecipientAdmission.reopen(new RecoveryRecipientAdmission.Request(authority,Path.of(text(facts,"admissionFile")),text(facts,"admissionSha256")),
            new RecoveryRecipientAdmission.RetainedRelease(approval,Path.of(text(scope,"storeRoot")),text(scope,"storeLockIdentity")));
        var qualification=RecoveryTemporaryQualification.reopen(new RecoveryTemporaryQualification.Request(authority,
            Path.of(text(facts,"qualificationFile")),text(facts,"qualificationSha256")),recipient);
        return new Reloaded(facts,request,qualification);
    }
    private record Reloaded(JsonNode facts,HandoverRecoveredPhysicalReview.Request request,RecoveryTemporaryQualification qualification) { }
    private RecoveryReceivedPreparationRestart() { }
}
