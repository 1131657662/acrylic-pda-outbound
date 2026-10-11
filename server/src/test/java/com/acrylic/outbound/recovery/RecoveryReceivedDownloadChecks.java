package com.acrylic.outbound.recovery;

import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.handover.HandoverRecoveredTemporaryDownload;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Existing actual login and independently retained admission consume original payload bytes without writes. */
public final class RecoveryReceivedDownloadChecks {
    public static void verify(WebApplicationContext app,Path attempt,Path original) throws Exception {
        RecoveryReceivedSessionChecks.verify(app,attempt,original);
        var jdbc=app.getBean(JdbcTemplate.class); var input=RecoveryReceivedSessionRestart.reload(jdbc,attempt,original);
        var snapshots=new E2aMigrationSnapshot(jdbc,attempt,attempt.resolve("storage")); var before=snapshots.capture();
        RecoveryDownloadTailChecks.verify(app,attempt,input); RecoveryDownloadConcurrentChecks.verify(app,attempt,input);
        assertThat(snapshots.capture()).isEqualTo(before);
        authenticate(app,attempt);
        try {
            var result=app.getBean(HandoverRecoveredTemporaryDownload.class).read(input.session(),input.request(),"recovered-download");
            var replay=app.getBean(HandoverRecoveredTemporaryDownload.class).read(input.session(),input.request(),"recovered-download-replay");
            assertThat(replay.manifest()).isEqualTo(result.manifest()); assertThat(replay.recoveryPackage()).isEqualTo(result.recoveryPackage());
            assertThat(snapshots.capture()).isEqualTo(before); bind(result,input.session());
            Files.write(attempt.resolve("download-manifest.json"),result.manifest()); Files.write(attempt.resolve("download-payload.json"),result.recoveryPackage());
            Files.writeString(attempt.resolve("download-before.json"),before.toPrettyString()); Files.writeString(attempt.resolve("download-after.json"),snapshots.capture().toPrettyString());
        } finally { SecurityContextHolder.clearContext(); }
        Files.writeString(attempt.resolve("download-restart-request.marker"),"Original recorded session only");
    }
    public static void verifyRestart(WebApplicationContext app,Path attempt,Path original) throws Exception {
        var jdbc=app.getBean(JdbcTemplate.class); var input=RecoveryReceivedSessionRestart.reload(jdbc,attempt,original);
        var snapshots=new E2aMigrationSnapshot(jdbc,attempt,attempt.resolve("storage")); var before=snapshots.capture();
        authenticate(app,attempt);
        try {
            var result=app.getBean(HandoverRecoveredTemporaryDownload.class).read(input.session(),input.request(),"download-restart");
            assertThat(result.manifest()).isEqualTo(Files.readAllBytes(attempt.resolve("download-manifest.json")));
            assertThat(result.recoveryPackage()).isEqualTo(Files.readAllBytes(attempt.resolve("download-payload.json")));
            assertThat(snapshots.capture()).isEqualTo(before);
        } finally { SecurityContextHolder.clearContext(); }
        Files.write(attempt.resolve("download-restart-result.json"),RecoveryControlJson.encode(SubmissionJson.object().put("pid",ProcessHandle.current().pid())
            .put("sourcePid",input.facts().path("sourcePid").asLong()).put("originalManifestAndBytes",true).put("databaseUnchanged",true).put("newLoginIssued",false)));
    }
    static void authenticate(WebApplicationContext app,Path attempt) throws Exception {
        var tokens=SubmissionJson.parse(Files.readAllBytes(attempt.resolve("private/recipient-login-response.json"))).path("data");
        var principal=app.getBean(com.acrylic.outbound.pda.PdaSessions.class).authenticate(tokens.path("accessToken").asText());
        SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,null,java.util.List.of()));
    }
    private static void bind(HandoverRecoveredTemporaryDownload.Result result,RecoveryRecipientSession session) {
        JsonNode manifest=SubmissionJson.parse(result.manifest()),body=SubmissionJson.parse(result.recoveryPackage()); var q=session.qualification().facts();
        assertThat(manifest.path("recipientSessionSha256").asText()).isEqualTo(session.sha256());
        assertThat(manifest.path("preparedTicket").path("temporaryQualification")).isEqualTo(session.qualification().preparationDescriptor());
        assertThat(manifest.path("recoveryPackageSha256").asText()).isEqualTo(sha256(result.recoveryPackage())).isEqualTo(q.path("source").path("payloadSha256").asText());
        assertThat(body.path("sourceRecoveryEpoch")).isEqualTo(q.path("source").path("sourceRecoveryEpoch")).isNotEqualTo(q.path("control").path("transportRecoveryEpoch"));
        assertThat(manifest.path("sourceKind").asText()).isEqualTo("TEMP"); assertThat(manifest.path("packageKind").asText()).isEqualTo("TEMP_FROZEN_PAYLOAD");
        manifest.path("capabilities").forEach(value->assertThat(value.asBoolean()).isFalse());
        assertThat(result.mayResumeScanning()).isFalse(); assertThat(result.localDeletionAllowed()).isFalse();
    }
    private RecoveryReceivedDownloadChecks() { }
}
