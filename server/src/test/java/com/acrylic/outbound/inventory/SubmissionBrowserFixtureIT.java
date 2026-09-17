package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc
class SubmissionBrowserFixtureIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Test void prepareIsolatedShortShipmentForBrowserReview() throws Exception {
        var identity=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");
        var f=new SubmissionFixture(new PublicationDriver(mvc,json,identity),jdbc,true,true);
        assertThat(f.submit().path("status").asText()).isEqualTo("REVIEW_REQUIRED");
        var fixture=json.createObjectNode().put("username",identity.user().username()).put("password","Test-password-123")
            .put("warehouseId",identity.warehouse().toString()).put("orderId",f.order).put("submissionId",f.id());
        Files.writeString(Path.of("build","submission-browser-fixture.json"),json.writeValueAsString(fixture));
    }
}
