package com.acrylic.outbound.recovery;

import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.handover.HandoverRecoveredRecipientSession;
import com.acrylic.outbound.handover.HandoverRecoveredPhysicalReview;
import com.acrylic.outbound.pda.PdaSessions;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Original independently pinned bytes and private bearer survive a JVM change without any new login. */
public final class RecoveryReceivedSessionRestart {
    public static void verify(WebApplicationContext app,Path attempt,Path original) throws Exception {
        var jdbc=app.getBean(JdbcTemplate.class); var input=reload(jdbc,attempt,original);
        assertThat(input.facts().path("sourcePid").asLong()).isNotEqualTo(ProcessHandle.current().pid());
        var tokens=SubmissionJson.parse(Files.readAllBytes(attempt.resolve("private/recipient-login-response.json"))).path("data");
        var principal=app.getBean(PdaSessions.class).authenticate(tokens.path("accessToken").asText());
        var snapshots=new E2aMigrationSnapshot(jdbc,attempt,attempt.resolve("storage")); var before=snapshots.capture();
        SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,null,java.util.List.of()));
        try {
            var result=app.getBean(HandoverRecoveredRecipientSession.class).admit(input.session(),input.request(),"session-restart");
            assertThat(result.signedSha256()).isEqualTo(text(input.facts(),"sessionSha256")); assertThat(snapshots.capture()).isEqualTo(before);
        } finally { SecurityContextHolder.clearContext(); }
        Files.write(attempt.resolve("session-restart-result.json"),RecoveryControlJson.encode(SubmissionJson.object()
            .put("pid",ProcessHandle.current().pid()).put("sourcePid",input.facts().path("sourcePid").asLong()).put("originalPinsReloaded",true)
            .put("newLoginIssued",false).put("newSignatureIssued",false).put("databaseUnchanged",true).put("ordinaryWritesAllowed",false)
            .put("localDeletionAllowed",false).put("mayResumeScanning",false)));
    }
    private static Reloaded reload(JdbcTemplate jdbc,Path attempt,Path original) throws Exception {
        var facts=pinned(attempt,"session"); var q=pinned(attempt,"qualification"); var scope=pinned(attempt,"scope");
        var request=RecoveryReceivedScopeReleaseReload.request(jdbc,attempt,original);
        var authority=new RecoveryOperationsAuthority(request.reconciliation().authority(),Path.of(text(q,"identityFile")),text(q,"identitySha256"));
        var approval=new RecoveryOperationsApproval.Request(authority,Path.of(text(scope,"approvalFile")),text(scope,"approvalSha256"));
        var recipient=RecoveryRecipientAdmission.reopen(new RecoveryRecipientAdmission.Request(authority,Path.of(text(q,"admissionFile")),text(q,"admissionSha256")),
            new RecoveryRecipientAdmission.RetainedRelease(approval,Path.of(text(scope,"storeRoot")),text(scope,"storeLockIdentity")));
        var qualification=RecoveryTemporaryQualification.reopen(new RecoveryTemporaryQualification.Request(authority,
            Path.of(text(q,"qualificationFile")),text(q,"qualificationSha256")),recipient);
        var session=RecoveryRecipientSession.reopen(new RecoveryRecipientSession.Request(authority,Path.of(text(facts,"sessionFile")),text(facts,"sessionSha256")),qualification);
        return new Reloaded(facts,request,session);
    }
    private static JsonNode pinned(Path attempt,String name) throws Exception {
        byte[] bytes=Files.readAllBytes(attempt.resolve(name+"-restart-input.json"));
        assertThat(sha256(bytes)).isEqualTo(Files.readString(attempt.resolve(name+"-restart-input.sha256"))); return readCanonical(bytes);
    }
    private record Reloaded(JsonNode facts,HandoverRecoveredPhysicalReview.Request request,RecoveryRecipientSession session) { }
    private RecoveryReceivedSessionRestart() { }
}
