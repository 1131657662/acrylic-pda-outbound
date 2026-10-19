package com.acrylic.outbound.inventory;

import com.acrylic.outbound.e2averify.E2aTestPorts;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/** FORMAL V1 compatibility sample using real submit/archive/read HTTP in the isolated application. */
public final class FormalArchiveRecoveryFixture {
    public record Original(String submission,String token,JsonNode receipt,JsonNode proof) { }

    public Original create(WebApplicationContext context) throws Exception {
        var jdbc=context.getBean(JdbcTemplate.class);var json=context.getBean(ObjectMapper.class);
        var identity=IdentityFixture.create(jdbc,context.getBean(PasswordEncoder.class),"WAREHOUSE_ADMIN");
        var mvc=MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean(com.acrylic.outbound.common.RequestIdFilter.class)).apply(springSecurity()).build();
        var fixture=new SubmissionFixture(new PublicationDriver(mvc,json,identity),jdbc,false,false);
        String token=fixture.tokens.path("accessToken").asText();
        var receipt=http("/pda/orders/"+fixture.order+"/submissions",token,fixture.body);
        assertThat(receipt.path("status").asText()).isEqualTo("SUCCEEDED");
        var request=SubmissionJson.object().put("payloadSha256",SubmissionJson.sha(fixture.body)).put("receiptSha256",SubmissionJson.sha(receipt));
        var proof=http("/pda/submissions/"+fixture.id()+"/archive-confirmation",token,request);
        assertThat(proof.path("archiveVersion").asInt()).isEqualTo(1);
        return new Original(fixture.id(),token,receipt,proof);
    }

    public void check(Original original) throws Exception {
        assertThat(http("/pda/submissions/"+original.submission(),original.token(),null)).isEqualTo(original.receipt());
        var request=SubmissionJson.object();
        for(String name:java.util.List.of("payloadSha256","receiptSha256"))request.set(name,original.proof().get(name));
        assertThat(http("/pda/submissions/"+original.submission()+"/archive-confirmation",original.token(),request)).isEqualTo(original.proof());
    }

    private JsonNode http(String path,String token,JsonNode body) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+E2aTestPorts.http()+"/api/v1"+path)).timeout(Duration.ofSeconds(20))
            .header("Authorization","Bearer "+token).header("X-Server-Recovery-Epoch","1");
        if(body==null)request.GET();
        else request.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(body)));
        try(var client=HttpClient.newHttpClient()) {
            var response=client.send(request.build(),HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as("FORMAL archive HTTP").isEqualTo(200);
            return SubmissionJson.parse(response.body()).path("data");
        }
    }
}
