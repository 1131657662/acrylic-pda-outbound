package com.acrylic.outbound.auth;

import com.acrylic.outbound.common.Ids;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name="outbound.bootstrap.enabled", havingValue="true")
public class BootstrapAdmin implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final Environment env;
    public BootstrapAdmin(JdbcTemplate jdbc, PasswordEncoder passwords, Environment env) {
        this.jdbc = jdbc; this.passwords = passwords; this.env = env;
    }

    @Override @Transactional public void run(String... args) {
        String username = env.getRequiredProperty("OUTBOUND_BOOTSTRAP_USERNAME");
        String password = env.getRequiredProperty("OUTBOUND_BOOTSTRAP_PASSWORD");
        if (!username.matches("[a-z0-9_-]{3,64}") || password.length() < 12 ||
            password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Bootstrap requires valid username and password of 12–72 UTF-8 bytes");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Long.class) != 0)
            throw new IllegalStateException("Bootstrap is only allowed in an empty identity database");
        UUID warehouse = Ids.next();
        UUID user = Ids.next();
        jdbc.update("INSERT INTO warehouse(id,code,name) VALUES(?,?,?)", Ids.bytes(warehouse), "MAIN", "主仓库");
        jdbc.update("INSERT INTO app_user(id,username,display_name,password_hash,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(3))",
            Ids.bytes(user), username, "库存管理员", passwords.encode(password));
        jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='WAREHOUSE_ADMIN'", Ids.bytes(user));
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)", Ids.bytes(user), Ids.bytes(warehouse));
        jdbc.update("""
            INSERT INTO operation_event(actor_id,event_type,entity_type,entity_id,details,server_time,request_id)
            VALUES(?,'BOOTSTRAP_ADMIN','USER',?,'{}',UTC_TIMESTAMP(3),'bootstrap')
            """, Ids.bytes(user), Ids.bytes(user));
    }
}
