package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties={"server.port=19434","outbound.rescue.delegation-enabled=true"}) @AutoConfigureMockMvc
class RescueProvisionHttpIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;@Autowired WebApplicationContext context;
    private final HttpClient client=HttpClient.newHttpClient();
    private RescueHttp fixture() throws Exception {
        var f=new RescueFixture(mvc,json,jdbc,passwords);
        // PDA login is separately assigned; rescue:read never grants login/scan rights by itself.
        jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='PDA_OPERATOR'",Ids.bytes(f.supervisor.identity().user().id()));
        return new RescueHttp(context,f);
    }
    private JsonNode recipient(RescueHttp h,PdaTestDriver.Device device) throws Exception {
        return new PdaTestDriver(h.f.supervisor).login(device);
    }
    private HttpResponse<byte[]> read(String grant,JsonNode tokens) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda/rescue-grants/"+grant))
            .header("Authorization","Bearer "+tokens.path("accessToken").asText()).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    private JsonNode grant(RescueHttp h) throws Exception {
        String id=h.create().path("requestId").asText();return h.data(h.approve(id,h.f.approval(),UUID.randomUUID()),200);
    }
    @Test void designatedRecipientOnOriginalDeviceReadsExactAuthorityAndLaterRevocation() throws Exception {
        var h=fixture();var grant=grant(h);String id=grant.path("grantId").asText();var tokens=recipient(h,h.f.device);
        var response=read(id,tokens);var material=h.data(response,200);
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(material.path("authorization")).isEqualTo(grant);assertThat(material.path("status").asText()).isEqualTo("AUTHORIZED");
        var revoked=h.data(h.revoke(id,SubmissionJson.object().put("reason","停止预置"),UUID.randomUUID()),200);
        var after=h.data(read(id,tokens),200);
        assertThat(after.path("status").asText()).isEqualTo("REVOKED");assertThat(after.path("revocation")).isEqualTo(revoked);
        assertThat(after.path("localDeletionAllowed").asBoolean()).isFalse();
    }
    @Test void originalOperatorAndAnotherDeviceCannotProvisionDelegatedIdentity() throws Exception {
        var h=fixture();String id=grant(h).path("grantId").asText();
        assertThat(read(id,h.tokens).statusCode()).isEqualTo(403);
        var other=h.f.pda.register();assertThat(read(id,recipient(h,other)).statusCode()).isEqualTo(404);
    }
    @Test void recipientPermissionRemovalInvalidatesAlreadyAuthenticatedProvisioning() throws Exception {
        var h=fixture();String id=grant(h).path("grantId").asText();var tokens=recipient(h,h.f.device);
        assertThat(read(id,tokens).statusCode()).isEqualTo(200);
        jdbc.update("DELETE ur FROM user_role ur INNER JOIN role r ON r.id=ur.role_id WHERE ur.user_id=? AND r.code='SUPERVISOR'",
            Ids.bytes(h.f.supervisor.identity().user().id()));
        assertThat(read(id,tokens).statusCode()).isEqualTo(403);
    }
}
