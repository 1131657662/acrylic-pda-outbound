package com.acrylic.outbound.recovery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class RecoveryFenceConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(RecoveryEffectConfiguration.class);
    @Test void bothFlagsRemainFalse() {
        var settings = new RecoveryEffectConfiguration.Settings();
        assertThat(settings.isEnabled()).isFalse(); assertThat(settings.isCrossProcessEnabled()).isFalse();
    }
    @Test void crossProcessWithoutEffectFlagFails() {
        runner.withPropertyValues("outbound.recovery.effect-gate.cross-process-enabled=true").run(c -> {
            assertThat(c).hasFailed(); assertThat(c.getStartupFailure()).hasStackTraceContaining("CONTROL_EFFECT_GATE_REQUIRED");
        });
    }
    @Test void crossProcessWithoutRealAuthorityFails() {
        runner.withPropertyValues("outbound.recovery.effect-gate.enabled=true", "outbound.recovery.effect-gate.cross-process-enabled=true")
            .withBean(RecoveryEffectGate.Authority.class, EffectTestAuthority::new).run(c -> {
                assertThat(c).hasFailed(); assertThat(c.getStartupFailure()).hasStackTraceContaining("CONTROL_REAL_AUTHORITY_REQUIRED");
            });
    }
    @Test void crossProcessWithoutAnyAuthorityFails() {
        runner.withPropertyValues("outbound.recovery.effect-gate.enabled=true", "outbound.recovery.effect-gate.cross-process-enabled=true").run(c -> {
            assertThat(c).hasFailed(); assertThat(c.getStartupFailure()).hasStackTraceContaining("CONTROL_REAL_AUTHORITY_REQUIRED");
        });
    }
}
