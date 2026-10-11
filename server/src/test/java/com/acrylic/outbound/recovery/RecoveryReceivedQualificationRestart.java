package com.acrylic.outbound.recovery;

import com.acrylic.outbound.auth.IdentityStore;
import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.handover.HandoverRecoveredTemporaryQualification;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Reloads original trust and exact bytes in a genuinely new JVM; missing grant cannot be reissued. */
public final class RecoveryReceivedQualificationRestart {
    public static void verify(WebApplicationContext app,Path attempt,Path original) throws Exception {
        byte[] input=Files.readAllBytes(attempt.resolve("qualification-restart-input.json"));
        assertThat(sha256(input)).isEqualTo(Files.readString(attempt.resolve("qualification-restart-input.sha256")));
        var facts=readCanonical(input); assertThat(facts.path("sourcePid").asLong()).isNotEqualTo(ProcessHandle.current().pid());
        var jdbc=app.getBean(JdbcTemplate.class); var request=RecoveryReceivedScopeReleaseReload.request(jdbc,attempt,original);
        byte[] scopeBytes=Files.readAllBytes(attempt.resolve("scope-restart-input.json"));
        assertThat(sha256(scopeBytes)).isEqualTo(Files.readString(attempt.resolve("scope-restart-input.sha256"))); var scope=readCanonical(scopeBytes);
        var authority=new RecoveryOperationsAuthority(request.reconciliation().authority(),Path.of(text(facts,"identityFile")),text(facts,"identitySha256"));
        var approval=new RecoveryOperationsApproval.Request(authority,Path.of(text(scope,"approvalFile")),text(scope,"approvalSha256"));
        var recipient=RecoveryRecipientAdmission.reopen(new RecoveryRecipientAdmission.Request(authority,Path.of(text(facts,"admissionFile")),text(facts,"admissionSha256")),
            new RecoveryRecipientAdmission.RetainedRelease(approval,Path.of(text(scope,"storeRoot")),text(scope,"storeLockIdentity")));
        var qualification=RecoveryTemporaryQualification.reopen(new RecoveryTemporaryQualification.Request(authority,
            Path.of(text(facts,"qualificationFile")),text(facts,"qualificationSha256")),recipient);
        var actor=app.getBean(IdentityStore.class).loadUserByUsername(text(facts,"registrarUsername"));
        var snapshots=new E2aMigrationSnapshot(jdbc,attempt,attempt.resolve("storage")); var before=snapshots.capture();
        RecoveryRecipientFixture.authenticate(actor);
        try {
            var result=app.getBean(HandoverRecoveredTemporaryQualification.class).issue(qualification,request);
            assertThat(result.signedSha256()).isEqualTo(text(facts,"qualificationSha256")); assertThat(snapshots.capture()).isEqualTo(before);
        } finally { SecurityContextHolder.clearContext(); }
        Files.write(attempt.resolve("qualification-restart-result.json"),RecoveryControlJson.encode(SubmissionJson.object()
            .put("pid",ProcessHandle.current().pid()).put("sourcePid",facts.path("sourcePid").asLong()).put("originalPinsReloaded",true)
            .put("newSignatureIssued",false).put("databaseUnchanged",true).put("ordinaryWritesAllowed",false)
            .put("localDeletionAllowed",false).put("firstRoundAllowed",false).put("mayResumeScanning",false)));
    }
    private RecoveryReceivedQualificationRestart() { }
}
