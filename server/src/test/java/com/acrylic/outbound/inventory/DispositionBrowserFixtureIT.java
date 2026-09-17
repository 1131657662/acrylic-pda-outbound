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

@SpringBootTest @AutoConfigureMockMvc
class DispositionBrowserFixtureIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Test void stoppedOrdersForBrowserAndFreshOrderForEmulator() throws Exception {
        var fixtures=json.createArrayNode();
        for(String action:new String[]{"RELEASE","QUARANTINE"}) {
            var identity=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");var web=new PublicationDriver(mvc,json,identity);
            var c=new ContinuationFixture(new SubmissionFixture(web,jdbc,true,false));
            fixtures.add(json.createObjectNode().put("action",action).put("username",identity.user().username()).put("password","Test-password-123")
                .put("warehouseId",identity.warehouse().toString()).put("orderId",c.r.f.order).put("barcode",c.r.body.at("/scans/0/barcode").asText()));
        }
        Files.writeString(Path.of("build/disposition-browser-fixture.json"),json.writeValueAsString(fixtures));
        var identity=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");var driver=new PdaTestDriver(new PublicationDriver(mvc,json,identity));
        var emulator=json.createObjectNode().put("username",identity.user().username()).put("password","Test-password-123")
            .put("warehouseId",identity.warehouse().toString()).put("orderId",driver.readyOrder());
        Files.writeString(Path.of("build/disposition-emulator-fixture.json"),json.writeValueAsString(emulator));
    }
}
