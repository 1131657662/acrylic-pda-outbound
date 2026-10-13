package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/** Fixture registration uses existing test helpers; challenges/login and credential operations use real HTTP. */
final class IndependentCredentialHttpWire {
    final JdbcTemplate jdbc;final PublicationDriver web;final PdaTestDriver pda;final PdaTestDriver.Device device;
    JsonNode tokens;String epoch="1";
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    IndependentCredentialHttpWire(WebApplicationContext context) throws Exception {
        jdbc=context.getBean(JdbcTemplate.class);
        var mvc=MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean(com.acrylic.outbound.common.RequestIdFilter.class)).apply(springSecurity()).build();
        web=new PublicationDriver(mvc,context.getBean(ObjectMapper.class),
            IdentityFixture.create(jdbc,context.getBean(PasswordEncoder.class),"WAREHOUSE_ADMIN"));
        pda=new PdaTestDriver(web);device=pda.register();login(device);
    }
    void login(PdaTestDriver.Device registered) throws Exception {
        login(registered,web.identity().user().username());
    }
    void login(PdaTestDriver.Device registered,String name) throws Exception {
        tokens=null;
        var challenge=data(post("/pda/auth/challenges",SubmissionJson.object().put("deviceId",registered.id())
            .put("purpose","LOGIN").put("bindingSha256",PdaTestDriver.sha(name)),null),200);
        tokens=data(post("/pda/auth/login",SubmissionJson.object().put("deviceId",registered.id()).put("username",name)
            .put("password","Test-password-123").put("challengeId",challenge.path("challengeId").asText())
            .put("signature",PdaTestDriver.sign(registered,challenge.path("signedText").asText())),null),200);
    }
    com.fasterxml.jackson.databind.node.ObjectNode command() { return SubmissionJson.object().put("deviceId",device.id())
        .put("deviceKeySha256",ImportFiles.sha256(device.key().getPublic().getEncoded())); }
    HttpResponse<byte[]> issue(UUID key) throws Exception { return post("/pda/independent-credentials",command(),key); }
    HttpResponse<byte[]> post(String path,JsonNode body,UUID key) throws Exception {
        var request=request(path).header("Content-Type","application/json").header("X-Server-Recovery-Epoch",epoch);
        if(key!=null)request.header("Idempotency-Key",key.toString());
        return http.send(request.POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(body))).build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    HttpResponse<byte[]> get(String id) throws Exception {
        return http.send(request("/pda/independent-credentials/"+id).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    private HttpRequest.Builder request(String path) {
        var result=HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1"+path)).timeout(Duration.ofSeconds(40));
        if(tokens!=null)result.header("Authorization","Bearer "+tokens.path("accessToken").asText());return result;
    }
    JsonNode data(HttpResponse<byte[]> response,int status) {
        assertThat(response.statusCode()).as("real HTTP: %s",new String(response.body(),java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(status);
        return SubmissionJson.parse(response.body()).path("data");
    }
    String code(HttpResponse<byte[]> response) { return SubmissionJson.parse(response.body()).path("code").asText(); }
    long count() { return jdbc.queryForObject("SELECT COUNT(*) FROM independent_capture_credential WHERE operator_id=?",Long.class,Ids.bytes(web.identity().user().id())); }
}
