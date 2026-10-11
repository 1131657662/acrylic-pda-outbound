package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.recovery.RecoveryReceivedDownloadHttpInput;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Standalone real product HTTP server between sequential Gradle runs, never a new login or signing authority. */
public final class E2aRecoveredAndroidBridgeMain {
    public static void main(String[] args) throws Exception {
        var profile=E2aCrossSourceProfile.completedSession();
        var options=java.util.stream.Stream.concat(java.util.stream.Stream.of("--outbound.handover.commands.enabled=true",
            "--outbound.handover.fault.enabled=true","--server.servlet.session.cookie.secure=false"),
            Arrays.stream(RecoveryReceivedDownloadHttpInput.options(profile.attempt))).toArray(String[]::new);
        try(var app=E2aCrossSourceHttpIT.start(profile,options)) {
            var snapshots=new E2aMigrationSnapshot(app.getBean(JdbcTemplate.class),profile.attempt,profile.attempt.resolve("storage"));
            var before=snapshots.capture(); saveInput(app.getBean(JdbcTemplate.class),profile.attempt);
            Files.writeString(profile.attempt.resolve("android-http-before.json"),before.toPrettyString());
            Files.writeString(profile.attempt.resolve("android-http-ready.marker"),Long.toString(ProcessHandle.current().pid()));
            long deadline=System.nanoTime()+Duration.ofMinutes(8).toNanos();
            while(!Files.exists(profile.attempt.resolve("android-http-done.marker"))) {
                if(System.nanoTime()>=deadline) throw new IllegalStateException("Android bridge deadline expired; preserve attempt");
                Thread.sleep(250);
            }
            var after=snapshots.capture(); Files.writeString(profile.attempt.resolve("android-http-after.json"),after.toPrettyString());
            assertThat(after).isEqualTo(before);
            Files.writeString(profile.attempt.resolve("android-http-server-result.json"),SubmissionJson.object()
                .put("pid",ProcessHandle.current().pid()).put("databaseAndFilesUnchanged",true).put("newLoginIssued",false).toString());
        }
    }
    private static void saveInput(JdbcTemplate jdbc,Path attempt) throws Exception {
        var envelope=SubmissionJson.parse(Files.readAllBytes(attempt.resolve("private/recipient-login-response.json")));
        var session=(com.fasterxml.jackson.databind.node.ObjectNode)envelope.path("data").deepCopy();
        session.put("serverRecoveryEpoch",envelope.path("serverRecoveryEpoch").asText()).put("localSessionId",UUID.randomUUID().toString());
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda/signing-key"))
            .header("Authorization","Bearer "+session.path("accessToken").asText()).timeout(Duration.ofSeconds(30)).GET().build();
        var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200); session.set("signingKey",SubmissionJson.parse(response.body()).path("data"));
        var manifest=SubmissionJson.parse(Files.readAllBytes(attempt.resolve("download-manifest.json")));
        String publicKey=jdbc.queryForObject("SELECT public_key FROM device WHERE id=?",String.class,Ids.bytes(UUID.fromString(session.path("deviceId").asText())));
        var input=SubmissionJson.object().put("baseUrl","http://127.0.0.1:19434/api/v1/").put("caseId",manifest.path("caseId").asText())
            .put("devicePublicKey",publicKey).put("originalDirectory",attempt.toString());
        input.set("session",session); Files.writeString(attempt.resolve("private/android-http-input.json"),input.toString());
    }
    private E2aRecoveredAndroidBridgeMain() { }
}
