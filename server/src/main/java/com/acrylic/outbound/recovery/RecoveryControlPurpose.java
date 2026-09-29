package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/**
 * Every authority load must declare <b>why</b> it loads, and the purpose decides which authority kind is
 * acceptable. The purpose is not a hint: an authority of the wrong kind is refused, never downgraded.
 *
 * <p>{@code authorityKind} is optional in the plain control-history shape (the pre-existing fixtures and
 * the closed-chain records carry none); a purpose that accepts the absent kind must say so explicitly.
 */
enum RecoveryControlPurpose {
    /** Admission, lease renewal and reads of the live (initial normal) authority. */
    NORMAL_ADMISSION(true, Set.of("INITIAL_NORMAL_INSTALL")),
    /**
     * Closed-chain projections. No closed authority identity exists yet (creating one belongs to B9), so
     * this purpose accepts nothing today and always fails closed.
     */
    CLOSED_PROJECTION(false, Set.of());

    private final boolean absentKindAllowed;
    private final Set<String> kinds;

    RecoveryControlPurpose(boolean absentKindAllowed, Set<String> kinds) {
        this.absentKindAllowed = absentKindAllowed;
        this.kinds = kinds;
    }

    void requireKind(JsonNode identity) { requireKind(identity, "CONTROL_AUTHORITY_PURPOSE_FORBIDDEN"); }

    /**
     * The same rule set serves every authority entry point; each entry keeps its own refusal code, so a
     * caller-visible code never changes merely because the rule was centralised.
     */
    void requireKind(JsonNode identity, String code) {
        String kind = identity.has("authorityKind") ? text(identity, "authorityKind") : "";
        RecoveryControlFormat.require(kind.isEmpty() ? absentKindAllowed : kinds.contains(kind), code);
    }
}
