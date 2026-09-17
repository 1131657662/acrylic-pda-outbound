package com.acrylic.outbound.inventory;

import com.acrylic.outbound.auth.SessionUser;
import com.acrylic.outbound.common.Ids;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

record IdentityFixture(SessionUser user, UUID warehouse) {
    static IdentityFixture create(JdbcTemplate jdbc, PasswordEncoder passwords, String role) {
        UUID user = Ids.next();
        UUID warehouse = Ids.next();
        String username = "test-" + user;
        String hash = passwords.encode("Test-password-123");
        jdbc.update("INSERT INTO warehouse(id,code,name) VALUES(?,?,?)", Ids.bytes(warehouse), "W-" + warehouse, "测试仓");
        jdbc.update("INSERT INTO app_user(id,username,display_name,password_hash,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(3))",
            Ids.bytes(user), username, "测试人员", hash);
        jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code=?", Ids.bytes(user), role);
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)", Ids.bytes(user), Ids.bytes(warehouse));
        return new IdentityFixture(new SessionUser(user, username, "测试人员", hash, 1, true), warehouse);
    }
}
