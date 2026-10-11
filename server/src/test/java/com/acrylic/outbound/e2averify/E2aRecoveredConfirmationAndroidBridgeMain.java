package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.recovery.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Actual first confirmation comes from Android; the original LOGIN and all prior business facts are retained. */
public final class E2aRecoveredConfirmationAndroidBridgeMain {
    public static void main(String[] args) throws Exception {
        var profile=E2aCrossSourceProfile.completedSession();
        var options=java.util.stream.Stream.concat(java.util.stream.Stream.of("--outbound.handover.commands.enabled=true",
            "--outbound.handover.fault.enabled=true","--server.servlet.session.cookie.secure=false"),
            java.util.stream.Stream.concat(Arrays.stream(RecoveryReceivedDownloadHttpInput.options(profile.attempt)),
                Arrays.stream(RecoveryReceivedConfirmationHttpSetup.options(profile.attempt)))).toArray(String[]::new);
        try(var app=E2aCrossSourceHttpIT.start(profile,options)) {
            var snapshots=new E2aMigrationSnapshot(app.getBean(JdbcTemplate.class),profile.attempt,profile.attempt.resolve("storage"));
            var before=snapshots.capture(); saveInput(app.getBean(JdbcTemplate.class),profile.attempt);
            Files.writeString(profile.attempt.resolve("confirmation-before.json"),before.toPrettyString());
            Files.writeString(profile.attempt.resolve("android-http-ready.marker"),Long.toString(ProcessHandle.current().pid()));
            long deadline=System.nanoTime()+Duration.ofMinutes(8).toNanos();
            while(!Files.exists(profile.attempt.resolve("android-http-done.marker"))) {
                if(System.nanoTime()>=deadline) throw new IllegalStateException("Confirmation bridge deadline expired; preserve attempt");
                Thread.sleep(250);
            }
            var after=snapshots.capture(); conserve(before,after);
            Files.writeString(profile.attempt.resolve("confirmation-after.json"),after.toPrettyString());
            Files.writeString(profile.attempt.resolve("confirmation-result.json"),SubmissionJson.object()
                .put("sourcePid",ProcessHandle.current().pid()).put("firstConfirmationThroughProductHttp",true).put("firstConfirmationFromAndroid",true).toString());
        }
    }
    private static void conserve(com.fasterxml.jackson.databind.JsonNode before,com.fasterxml.jackson.databind.JsonNode after) {
        assertThat(after.path("tables").size()).isEqualTo(before.path("tables").size());
        before.path("tables").fields().forEachRemaining(e->{if(!e.getKey().equals("recovery_handover_confirmation"))
            assertThat(after.path("tables").path(e.getKey())).as(e.getKey()).isEqualTo(e.getValue());});
        assertThat(before.path("tables").path("recovery_handover_confirmation").path("rows").size()).isZero();
        assertThat(after.path("tables").path("recovery_handover_confirmation").path("rows").size()).isEqualTo(1);
        assertThat(after.path("files")).isEqualTo(before.path("files")); assertThat(after.path("flywayHistory")).isEqualTo(before.path("flywayHistory"));
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
    private E2aRecoveredConfirmationAndroidBridgeMain() { }
}
