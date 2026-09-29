package com.acrylic.outbound.recovery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecoveryEffectConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(RecoveryEffectConfiguration.class);

    @Test void absentFlagCreatesDisabledGateWithoutAnyDatasource() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(RecoveryEffectGate.class).enabled()).isFalse();
            assertThat(context.getBeansOfType(javax.sql.DataSource.class)).isEmpty();
        });
    }

    @Test void enabledWithoutAuthorityFailsStartup() {
        runner.withPropertyValues("outbound.recovery.effect-gate.enabled=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("CONTROL_EFFECT_AUTHORITY_REQUIRED");
        });
    }

    @Test void enabledWithUnavailableAuthorityFailsStartup() throws Exception {
        var authority = mock(RecoveryEffectGate.Authority.class);
        doThrow(new java.sql.SQLException("CONTROL_AUTHORITY_UNAVAILABLE")).when(authority).validateStartup();
        runner.withBean(RecoveryEffectGate.Authority.class, () -> authority)
            .withPropertyValues("outbound.recovery.effect-gate.enabled=true").run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("CONTROL_AUTHORITY_UNAVAILABLE");
            });
    }

    @Test void enabledGateDoesNotWireAnyDatasource() {
        runner.withBean(RecoveryEffectGate.Authority.class, EffectTestAuthority::new)
            .withPropertyValues("outbound.recovery.effect-gate.enabled=true").run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(RecoveryEffectGate.class).enabled()).isTrue();
                assertThat(context.getBeansOfType(javax.sql.DataSource.class)).isEmpty();
            });
    }
}
