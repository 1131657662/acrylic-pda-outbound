package com.acrylic.outbound.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

/** Daily identity writes share E -> G before warehouse/device or identity-dependent DML. */
@Component
@Transactional(propagation=Propagation.MANDATORY)
public class IdentityWriteGuard {
    private final ServerState state;
    private final JdbcTemplate jdbc;
    public IdentityWriteGuard(ServerState state,JdbcTemplate jdbc) { this.state=state;this.jdbc=jdbc; }

    public void lock(String epoch) {
        state.lockWritable(epoch);
        identity();
    }

    public void authentication() {
        // Authentication already remains available in recovery; this is ordering, not a new capability gate.
        jdbc.queryForObject("SELECT id FROM server_state WHERE id=1 FOR SHARE",Integer.class);
        identity();
    }

    private void identity() {
        jdbc.queryForObject("SELECT id FROM user_management_guard WHERE id=1 FOR UPDATE",Integer.class);
    }
}
