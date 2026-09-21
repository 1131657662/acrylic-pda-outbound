package com.acrylic.outbound.pda;

import com.acrylic.outbound.common.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Session locks never acquire an implicit user lock through a JOIN. */
@Component
public class PdaSessionLocks {
    private final JdbcTemplate jdbc;
    public PdaSessionLocks(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Identity refresh(String token) {
        var rows=jdbc.query("SELECT BIN_TO_UUID(id),BIN_TO_UUID(user_id),BIN_TO_UUID(device_id) FROM pda_session WHERE refresh_hash=?",
            (rs,n)->new Identity(UUID.fromString(rs.getString(1)),UUID.fromString(rs.getString(2)),UUID.fromString(rs.getString(3))),
            com.acrylic.outbound.devices.DeviceCrypto.sha(token));
        if(rows.isEmpty()) throw expired();return rows.getFirst();
    }
    public void principal(PdaSessions.Principal principal) {
        lock(new Identity(principal.sessionId(),principal.userId(),principal.deviceId()));
    }
    public void lock(Identity before) {
        var rows=jdbc.query("SELECT BIN_TO_UUID(id),BIN_TO_UUID(user_id),BIN_TO_UUID(device_id) FROM pda_session WHERE id=? FOR UPDATE",
            (rs,n)->new Identity(UUID.fromString(rs.getString(1)),UUID.fromString(rs.getString(2)),UUID.fromString(rs.getString(3))),Ids.bytes(before.id()));
        if(rows.size()!=1 || !before.equals(rows.getFirst())) throw expired();
    }
    public void user(UUID user) { selected("SELECT BIN_TO_UUID(id) FROM pda_session WHERE user_id=?",user); }
    public void device(UUID device) { selected("SELECT BIN_TO_UUID(id) FROM pda_session WHERE device_id=?",device); }
    private void selected(String sql,UUID owner) {
        jdbc.queryForList(sql,String.class,Ids.bytes(owner)).stream().sorted()
            .forEach(id->jdbc.queryForList("SELECT id FROM pda_session WHERE id=? FOR UPDATE",Ids.bytes(UUID.fromString(id))));
    }
    private BusinessException expired() {
        return new BusinessException(401,"AUTH_EXPIRED","PDA登录已失效，请重新认证；保留本机数据");
    }
    public record Identity(UUID id,UUID user,UUID device) { }
}
