package com.acrylic.outbound.recovery;

import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Actual production routes, configured trust and bearer filters; no test controller or request trust. */
public final class RecoveryReceivedDownloadHttpChecks {
    public static void verify(WebApplicationContext app,Path attempt,boolean restart) throws Exception {
        var jdbc=app.getBean(JdbcTemplate.class); var snapshot=new E2aMigrationSnapshot(jdbc,attempt,attempt.resolve("storage")); var before=snapshot.capture();
        var manifest=SubmissionJson.parse(Files.readAllBytes(attempt.resolve("download-manifest.json")));
        String base="/handovers/"+manifest.path("caseId").asText(),prefix=restart?"download-http-restart":"download-http";
        String token=SubmissionJson.parse(Files.readAllBytes(attempt.resolve("private/recipient-login-response.json"))).path("data").path("accessToken").asText();
        for(String kind:new String[]{"manifest","recovery-package"}) download(attempt,base,token,prefix,kind);
        assertThat(get(base+"/manifest",null).statusCode()).isEqualTo(401);
        assertThat(get("/handovers/"+java.util.UUID.randomUUID()+"/manifest",token).statusCode()).isEqualTo(404);
        if(!restart) {
            tampered(attempt,base,token);
            RecoveryDownloadHttpNegativeChecks.verify(app,attempt,new RecoveryDownloadHttpNegativeChecks.Request(base,token,manifest));
            RecoveryDownloadHttpInputTailChecks.verify(app,attempt,manifest.path("caseId").asText());
        }
        assertThat(snapshot.capture()).isEqualTo(before);
        Files.writeString(attempt.resolve(prefix+"-before.json"),before.toPrettyString());
        Files.writeString(attempt.resolve(prefix+"-after.json"),snapshot.capture().toPrettyString());
        Files.write(attempt.resolve(prefix+"-result.json"),RecoveryControlJson.encode(SubmissionJson.object().put("pid",ProcessHandle.current().pid())
            .put("actualProductRoutes",true).put("originalManifestAndBytes",true).put("databaseUnchanged",true).put("restart",restart)
            .put("missingBearerHttp",401).put("wrongCaseHttp",404).put("tamperedInputHttp",restart?0:503).put("requestTrustIgnored",true)
            .put("mayResumeScanning",false).put("localDeletionAllowed",false)));
    }
    private static void download(Path attempt,String base,String token,String prefix,String kind) throws Exception {
            var response=get(base+"/"+kind,token); Files.write(attempt.resolve(prefix+"-"+kind+".json"),response.body());
            var metadata=SubmissionJson.object().put("status",response.statusCode())
                .put("cacheControl",response.headers().firstValue("Cache-Control").orElse(""))
                .put("nosniff",response.headers().firstValue("X-Content-Type-Options").orElse(""))
                .put("contentLength",response.headers().firstValue("Content-Length").orElse(""));
            Files.write(attempt.resolve(prefix+"-"+kind+"-metadata.json"),RecoveryControlJson.encode(metadata));
            assertThat(response.statusCode()).as("Product GET response: %s",new String(response.body(),java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(200);
            assertThat(response.body()).isEqualTo(Files.readAllBytes(attempt.resolve(kind.equals("manifest")?"download-manifest.json":"download-payload.json")));
            assertThat(get(base+"/"+kind+"?path=ignored&pin=ignored",token).body()).isEqualTo(response.body());
            assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
            assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
            assertThat(response.headers().firstValue("Content-Length")).contains(Integer.toString(response.body().length));
    }
    private static void tampered(Path attempt,String base,String token) throws Exception {
        Path file=attempt.resolve("download-http-input.json"); byte[] retained=Files.readAllBytes(file);
        try {
            Files.writeString(file,"tampered configured input"); var response=get(base+"/manifest",token);
            assertThat(response.statusCode()).isEqualTo(503);
            assertThat(SubmissionJson.parse(response.body()).path("code").asText()).isEqualTo("RECOVERY_DOWNLOAD_INPUT_PIN_MISMATCH");
            Files.write(attempt.resolve("download-http-tampered-input.json"),response.body());
        } finally { Files.write(file,retained); }
    }
    static HttpResponse<byte[]> get(String path,String token) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda"+path)).timeout(Duration.ofSeconds(30)).GET();
        if(token!=null) request.header("Authorization","Bearer "+token);
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().send(request.build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    private RecoveryReceivedDownloadHttpChecks() { }
}
