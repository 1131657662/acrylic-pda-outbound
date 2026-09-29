package com.acrylic.outbound.recovery;

import java.sql.SQLException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates only a gate. Does not replace any datasource or wire HTTP/Session. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RecoveryEffectConfiguration.Settings.class)
public class RecoveryEffectConfiguration {
    @Bean
    RecoveryEffectGate recoveryEffectGate(Settings settings, ObjectProvider<RecoveryEffectGate.Authority> authority)
            throws SQLException {
        if (!settings.isEnabled()) {
            if (settings.isCrossProcessEnabled()) throw new SQLException("CONTROL_EFFECT_GATE_REQUIRED");
            return RecoveryEffectGate.disabled();
        }
        var supplied = authority.getIfAvailable();
        if (settings.isCrossProcessEnabled() && !(supplied instanceof RecoveryFenceAuthority))
            throw new SQLException("CONTROL_REAL_AUTHORITY_REQUIRED");
        if (supplied instanceof RecoveryFenceAuthority && !settings.isCrossProcessEnabled())
            throw new SQLException("CONTROL_CROSS_PROCESS_OPT_IN_REQUIRED");
        return RecoveryEffectGate.enabled(supplied);
    }

    @ConfigurationProperties("outbound.recovery.effect-gate")
    public static class Settings {
        private boolean enabled = false;
        private boolean crossProcessEnabled = false;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isCrossProcessEnabled() { return crossProcessEnabled; }
        public void setCrossProcessEnabled(boolean enabled) { crossProcessEnabled = enabled; }
    }
}
