package com.acrylic.outbound.recovery;

/** Diagnostics are observations, never substitutes for a held kernel lock. */
final class RecoveryFenceAudit {
    private RecoveryFenceAudit() { }
    static void event(String code, Object detail) {
        System.getLogger(RecoveryFenceAudit.class.getName()).log(System.Logger.Level.WARNING,
            code + "|" + detail);
    }
    static void require(boolean valid, String code, Object detail) {
        if (!valid) { event(code, detail); throw new RecoveryControlException(code); }
    }
}
