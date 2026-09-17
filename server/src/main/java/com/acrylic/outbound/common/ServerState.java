package com.acrylic.outbound.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ServerState {
    private final JdbcTemplate jdbc;
    public ServerState(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public State current() {
        return jdbc.queryForObject("SELECT recovery_epoch, service_mode FROM server_state WHERE id=1",
            (rs, row) -> new State(rs.getString(1), rs.getString(2)));
    }

    public void requireWritable(String expectedEpoch) {
        validate(current(), expectedEpoch);
    }

    public void lockWritable(String expectedEpoch) {
        State state = jdbc.queryForObject("SELECT recovery_epoch, service_mode FROM server_state WHERE id=1 FOR SHARE",
            (rs, row) -> new State(rs.getString(1), rs.getString(2)));
        validate(state, expectedEpoch);
    }

    private void validate(State state, String expectedEpoch) {
        if (!"NORMAL".equals(state.mode()))
            throw new BusinessException(503, "SERVER_RECOVERY_REQUIRED", "服务器正在恢复对账，暂不能写入");
        if (!state.epoch().equals(expectedEpoch))
            throw new BusinessException(409, "SERVER_RECOVERY_EPOCH_MISMATCH", "服务器恢复代次已变化，请刷新后重试");
    }
    public record State(String epoch, String mode) { }
}
