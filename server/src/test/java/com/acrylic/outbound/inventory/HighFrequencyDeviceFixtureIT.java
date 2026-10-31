package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic isolated account and stock only; Android creates its own device key and preparation. */
@SpringBootTest @AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="HIGH_FREQUENCY_DEVICE_INPUT", matches=".+")
class HighFrequencyDeviceFixtureIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;

    @Test void createsTemporaryCapableAccountWithoutDeviceOrPreparation() throws Exception {
        var path = inputPath();
        var identity = IdentityFixture.create(jdbc, passwords, "WAREHOUSE_ADMIN");
        jdbc.update("INSERT INTO user_role SELECT UUID_TO_BIN(?),id FROM role WHERE code='TEMPORARY_ORDER_CREATOR'",
            identity.user().id().toString());
        var orders = new OrderTestDriver(new PublicationDriver(mvc, json, identity));
        orders.stock();
        orders.policy("0", false);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM device", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM temporary_authorization", Integer.class)).isZero();
        var input = json.createObjectNode().put("purpose", "SYNTHETIC_HIGH_FREQUENCY_UI")
            .put("baseUrl", System.getenv("HIGH_FREQUENCY_HTTP_BASE"))
            .put("warehouseId", identity.warehouse().toString())
            .put("username", identity.user().username()).put("password", "Test-password-123");
        Files.writeString(path, json.writeValueAsString(input), StandardOpenOption.CREATE_NEW);
    }

    private Path inputPath() {
        var path = Path.of(System.getenv("HIGH_FREQUENCY_DEVICE_INPUT")).toAbsolutePath().normalize();
        assertThat(path.toString().replace('\\', '/'))
            .startsWith("D:/acrylic-pda-outbound/.local/goal-20261018-resume/");
        assertThat(Files.isDirectory(path.getParent())).isTrue();
        assertThat(Files.exists(path)).isFalse();
        assertThat(System.getenv("HIGH_FREQUENCY_HTTP_BASE")).matches("http://127\\.0\\.0\\.1:[1-9][0-9]{3,4}/api/v1/");
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).matches("acrylic_e2a_cross_20260925_[0-9]+");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class)).isZero();
        return path;
    }
}
