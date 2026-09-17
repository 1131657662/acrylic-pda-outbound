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
class RecallBrowserFixtureIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Test void prepareEmptyStoppedAndAssignedOrdersForBrowser() throws Exception {
        var identity=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");var web=new PublicationDriver(mvc,json,identity);
        var r=new RecallFixture(new SubmissionFixture(web,jdbc,true,false));r.start(true);r.upload();r.stop();
        var fresh=new SubmissionFixture(r.f);
        var fixture=json.createObjectNode().put("username",identity.user().username()).put("password","Test-password-123")
            .put("warehouseId",identity.warehouse().toString()).put("stoppedOrderId",r.f.order).put("assignedOrderId",fresh.order)
            .put("changeRequestId",r.recall.get("changeRequestId").asText());
        Files.writeString(Path.of("build","recall-browser-fixture.json"),json.writeValueAsString(fixture));
    }
    @Test void prepareFreshOrderForRecallEmulator() throws Exception {
        var identity=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");
        var driver=new PdaTestDriver(new PublicationDriver(mvc,json,identity));String order=driver.readyOrder();
        var fixture=json.createObjectNode().put("username",identity.user().username()).put("password","Test-password-123")
            .put("warehouseId",identity.warehouse().toString()).put("orderId",order);
        Files.writeString(Path.of("build","recall-emulator-fixture.json"),json.writeValueAsString(fixture));
    }
}
