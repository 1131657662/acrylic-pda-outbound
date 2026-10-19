package com.acrylic.outbound.recovery;

import java.sql.SQLException;

/** MODEL authority decision input. It does not implement or prove P5/fenced.lock. */
final class EffectTestAuthority implements RecoveryEffectGate.Authority {
    int checks;
    int localFences;
    int unknownFences;
    String denial;
    @Override public void validateStartup() { }
    @Override public void checkEffect() throws SQLException {
        checks++;
        if (denial != null) throw new SQLException(denial);
    }
    @Override public void fenceLocal() { localFences++; }
    @Override public void fenceUnknown() { unknownFences++; }
}
