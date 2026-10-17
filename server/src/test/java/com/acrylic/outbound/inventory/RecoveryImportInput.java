package com.acrylic.outbound.inventory;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Minimal synthetic admin/viewer identities; no original inventory or identity is consumed. */
public record RecoveryImportInput(String username, String user, String warehouse, String viewer) {
    public static RecoveryImportInput seed(ConfigurableApplicationContext web) {
        var jdbc = web.getBean(JdbcTemplate.class); var passwords = web.getBean(PasswordEncoder.class);
        var admin = IdentityFixture.create(jdbc, passwords, "WAREHOUSE_ADMIN");
        var viewer = IdentityFixture.create(jdbc, passwords, "WAREHOUSE_VIEWER");
        jdbc.update("INSERT INTO user_warehouse VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?))",
            viewer.user().id().toString(), admin.warehouse().toString());
        return new RecoveryImportInput(admin.user().username(), admin.user().id().toString(),
            admin.warehouse().toString(), viewer.user().username());
    }
}
