package com.acrylic.outbound.auth;

import com.acrylic.outbound.common.Ids;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Repository;

@Repository
public class IdentityStore implements UserDetailsService {
    private final JdbcTemplate jdbc;
    public IdentityStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public SessionUser loadUserByUsername(String username) {
        var users = jdbc.query("""
            SELECT BIN_TO_UUID(id) AS id,username,display_name,password_hash,auth_version,status
            FROM app_user WHERE username=?
            """, (rs, n) -> new SessionUser(UUID.fromString(rs.getString("id")), rs.getString("username"),
            rs.getString("display_name"), rs.getString("password_hash"), rs.getLong("auth_version"),
            rs.getString("status").equals("ACTIVE")), username);
        if (users.isEmpty()) throw new UsernameNotFoundException("Invalid credentials");
        return users.getFirst();
    }

    public List<String> permissions(UUID userId) {
        return jdbc.queryForList("""
            SELECT p.code FROM permission p WHERE EXISTS (
                SELECT 1 FROM role_permission rp INNER JOIN user_role ur ON ur.role_id=rp.role_id
                WHERE rp.permission_id=p.id AND ur.user_id=?) ORDER BY p.code
            """, String.class, Ids.bytes(userId));
    }

    public List<Warehouse> warehouses(UUID userId) {
        return jdbc.query("""
            SELECT BIN_TO_UUID(w.id),w.code,w.name FROM warehouse w
            INNER JOIN user_warehouse uw ON uw.warehouse_id=w.id
            WHERE uw.user_id=? AND w.status='ACTIVE' ORDER BY w.code
            """, (rs, n) -> new Warehouse(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3)),
            Ids.bytes(userId));
    }
    public record Warehouse(UUID id, String code, String name) { }
}
