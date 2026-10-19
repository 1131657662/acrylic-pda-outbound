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

/** Fresh synthetic account only; Android must register its own key and obtain a real credential. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "INDEPENDENT_WORK_UI_INPUT", matches = ".+")
class IndependentWorkUiFixtureIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;

    @Test void preparesFreshAccountForNativeWorkUi() throws Exception {
        var path = guardedInput();
        var identity = IdentityFixture.create(jdbc, passwords, "WAREHOUSE_ADMIN");
        var input = json.createObjectNode().put("purpose", "SYNTHETIC_NATIVE_WORK_UI")
            .put("baseUrl", "http://127.0.0.1:19498/api/v1/")
            .put("warehouseId", identity.warehouse().toString())
            .put("username", identity.user().username()).put("password", "Test-password-123");
        Files.writeString(path, json.writeValueAsString(input), StandardOpenOption.CREATE_NEW);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM device", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM independent_capture_credential", Integer.class)).isZero();
    }

    private Path guardedInput() {
        var root = Path.of("D:/acrylic-pda-outbound/.local/u04-independent-native-input-run01");
        var path = Path.of(System.getenv("INDEPENDENT_WORK_UI_INPUT")).toAbsolutePath().normalize();
        assertThat(path).isEqualTo(root.resolve("private/input.json"));
        assertThat(Files.isDirectory(path.getParent())).isTrue();
        assertThat(Files.exists(path)).isFalse();
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("acrylic_u04_native_input01");
        assertThat(jdbc.queryForObject("SELECT @@port", Integer.class)).isEqualTo(19398);
        var datadir = jdbc.queryForObject("SELECT @@datadir", String.class).replace('\\', '/');
        assertThat(datadir).isEqualToIgnoringCase(root.resolve("database/data").toString().replace('\\', '/') + "/");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class)).isZero();
        return path;
    }
}
