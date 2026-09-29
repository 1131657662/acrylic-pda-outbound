package com.acrylic.outbound.recovery;

import java.sql.SQLException;

/** B1-1 delegate boundary only. No production authority or cross-process protocol is supplied. */
public final class RecoveryEffectGate {
    private final Authority authority;
    private volatile boolean fenced;

    /** Implementations must validate their trusted source; a missing source must never allow effects. */
    public interface Authority {
        void validateStartup() throws SQLException;
        void checkEffect() throws SQLException;
    }

    private RecoveryEffectGate(Authority authority) { this.authority = authority; }

    public static RecoveryEffectGate disabled() { return new RecoveryEffectGate(null); }

    public static RecoveryEffectGate enabled(Authority authority) throws SQLException {
        if (authority == null) throw new SQLException("CONTROL_EFFECT_AUTHORITY_REQUIRED");
        authority.validateStartup();
        return new RecoveryEffectGate(authority);
    }

    boolean enabled() { return authority != null; }

    /** Shared by all explicitly attached connections; no disk or lock is needed for this signal. */
    public void fence() { if (enabled()) fenced = true; }

    void check() throws SQLException {
        if (!enabled()) return;
        if (fenced) throw new SQLException("RECOVERY_LEASE_FENCED");
        try { authority.checkEffect(); }
        catch (SQLException | RuntimeException error) {
            fence();
            throw error;
        }
    }

    static SQLException unclassified() { return new SQLException("CONTROL_EFFECT_UNCLASSIFIED"); }
}
