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
class PdaAndroidFixtureIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Test void createIsolatedPublishedOrderForEmulatorEndToEndCheck() throws Exception {
        var identity=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");
        var driver=new PdaTestDriver(new PublicationDriver(mvc,json,identity));String order=driver.readyOrder();
        assertThat(new OrderTestDriver(driver.web()).detail(order).at("/order/status").asText()).isEqualTo("READY");
        var body=json.createObjectNode().put("username",identity.user().username()).put("password","Test-password-123")
            .put("warehouseId",identity.warehouse().toString()).put("orderId",order);
        Files.writeString(Path.of("build","pda-emulator-fixture.json"),json.writeValueAsString(body));
    }
}
