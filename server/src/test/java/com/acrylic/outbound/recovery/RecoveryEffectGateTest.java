package com.acrylic.outbound.recovery;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecoveryEffectGateTest {
    @Test void coordinatorBusyPropagatesWithoutLocalFence() throws Exception {
        var authority = new EffectTestAuthority();
        authority.denial = "CONTROL_COORDINATOR_BUSY";
        var gate = RecoveryEffectGate.enabled(authority);

        assertThatThrownBy(gate::check).hasMessage("CONTROL_COORDINATOR_BUSY");

        assertThat(authority.checks).isEqualTo(1);
        assertThat(authority.localFences).isZero();
    }

    @Test void nonBusyAuthorityFailureStillFencesLocally() throws Exception {
        var authority = new EffectTestAuthority();
        authority.denial = "RECOVERY_LEASE_OUTSTANDING";
        var gate = RecoveryEffectGate.enabled(authority);

        assertThatThrownBy(gate::check).hasMessage("RECOVERY_LEASE_OUTSTANDING");

        assertThat(authority.checks).isEqualTo(1);
        assertThat(authority.localFences).isEqualTo(1);
    }
}
