package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest @AutoConfigureMockMvc
class PairingBrowserFixtureIT {
    @Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;@Autowired MockMvc mvc;
    @Test void createsIsolatedPairingOperatorAndAdministrator() throws Exception {
        var admin=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");
        String order=new PdaTestDriver(new PublicationDriver(mvc,json,admin)).readyOrder();
        var value=json.createObjectNode().put("username",admin.user().username()).put("password","Test-password-123")
            .put("warehouseId",admin.warehouse().toString()).put("baseUrl","http://127.0.0.1:18083/api/v1/").put("orderId",order);
        Files.writeString(Path.of("build/pairing-browser-fixture.json"),json.writeValueAsString(value));
    }
}
