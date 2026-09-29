package com.acrylic.outbound.recovery;

/** F1 is published by the caller first. A returned halt never counts as confirmed termination. */
final class RecoveryFenceSequence {
    interface Actions {
        void hold() throws Exception;
        void audit() throws Exception;
        void release() throws Exception;
        void close() throws Exception;
        void halt();
    }
    private RecoveryFenceSequence() { }
    static void run(Actions actions) {
        boolean signal = attempt("FENCE_F1_PRIME", actions::hold);
        attempt("FENCE_F2_AUDIT", actions::audit);
        boolean released = attempt("FENCE_F3_RELEASE", actions::release);
        if (released) attempt("FENCE_F3_CLOSE", actions::close);
        RecoveryFenceAudit.event("FENCE_F4_WRITERS_RETAINED", "caller poisoned writer before sequence");
        try { actions.halt(); }
        catch (RuntimeException error) { RecoveryFenceAudit.event("FENCE_F5_UNCONFIRMED", error); }
        RecoveryFenceAudit.event(signal || released ? "FENCE_CONDITIONAL_CROSS_PROCESS_BASIS"
            : "UNCONFIRMED_NO_CROSS_PROCESS_BASIS", "F1prime=" + signal + "|F3=" + released + "|F5=unconfirmed|external isolation if no basis");
    }
    @FunctionalInterface private interface Step { void run() throws Exception; }
    private static boolean attempt(String step, Step action) {
        try { action.run(); RecoveryFenceAudit.event(step + "_CONFIRMED", "returned normally"); return true; }
        catch (Exception error) { RecoveryFenceAudit.event(step + "_UNCONFIRMED", error); return false; }
    }
}
