package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/** Synthetic setup through existing helpers; every operation under test uses real loopback HTTP. */
final class ReadOnlyNameHttpWire {
    final WebApplicationContext context;final JdbcTemplate jdbc;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    ReadOnlyNameHttpWire(WebApplicationContext context) { this.context=context;this.jdbc=context.getBean(JdbcTemplate.class); }
    TemporaryFixture seed() throws Exception {
        var mvc=MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean(com.acrylic.outbound.common.RequestIdFilter.class)).apply(springSecurity()).build();
        var web=new PublicationDriver(mvc,context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class),
            IdentityFixture.create(jdbc,context.getBean(PasswordEncoder.class),"WAREHOUSE_ADMIN"));
        var f=new TemporaryFixture(web,jdbc);f.stock();return f;
    }
    HttpResponse<byte[]> call(String method,String path,JsonNode body,UUID key,JsonNode tokens) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+System.getenv("E2A_HTTP_PORT")+"/api/v1"+path))
            .timeout(Duration.ofSeconds(40)).header("X-Server-Recovery-Epoch","1");
        if(tokens!=null)request.header("Authorization","Bearer "+tokens.path("accessToken").asText());
        if(key!=null)request.header("Idempotency-Key",key.toString());
        if(body!=null)request.header("Content-Type","application/json");
        return http.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():
            HttpRequest.BodyPublishers.ofString(body.toString())).build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    JsonNode data(HttpResponse<byte[]> response,int status) {
        assertThat(response.statusCode()).as("real HTTP: %s",new String(response.body(),java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(status);
        return com.acrylic.outbound.submissions.SubmissionJson.parse(response.body()).path("data");
    }
    JsonNode post(String path,JsonNode body,UUID key,JsonNode tokens) throws Exception { return data(call("POST",path,body,key,tokens),200); }
    JsonNode get(String path,JsonNode tokens) throws Exception { return data(call("GET",path,null,null,tokens),200); }
}
