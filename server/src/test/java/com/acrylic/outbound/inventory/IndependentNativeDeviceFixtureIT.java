package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic isolated account only. Android generates and registers its own key over real HTTP. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named="INDEPENDENT_NATIVE_DEVICE_INPUT", matches=".+")
class IndependentNativeDeviceFixtureIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;

    @Test void createsAccountWithoutDeviceSessionOrCredential() throws Exception {
        var path = inputPath();
        var identity = IdentityFixture.create(jdbc, passwords, "WAREHOUSE_ADMIN");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM device", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM independent_capture_credential", Integer.class)).isZero();
        var input = json.createObjectNode().put("purpose", "SYNTHETIC_INDEPENDENT_NATIVE")
            .put("baseUrl", System.getenv("INDEPENDENT_NATIVE_HTTP_BASE"))
            .put("warehouseId", identity.warehouse().toString())
            .put("username", identity.user().username()).put("password", "Test-password-123");
        Files.writeString(path, json.writeValueAsString(input), StandardOpenOption.CREATE_NEW);
    }

    private Path inputPath() {
        var path = Path.of(System.getenv("INDEPENDENT_NATIVE_DEVICE_INPUT")).toAbsolutePath().normalize();
        assertThat(path.toString().replace('\\', '/'))
            .startsWith("D:/acrylic-pda-outbound/.local/goal-20261018-resume/");
        assertThat(Files.isDirectory(path.getParent())).isTrue();
        assertThat(Files.exists(path)).isFalse();
        assertThat(System.getenv("INDEPENDENT_NATIVE_HTTP_BASE"))
            .matches("http://127\\.0\\.0\\.1:[1-9][0-9]{3,4}/api/v1/");
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("acrylic_e2a_cross_20260925_810");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class)).isZero();
        return path;
    }
}
