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
        return settings.isEnabled() ? RecoveryEffectGate.enabled(authority.getIfAvailable())
            : RecoveryEffectGate.disabled();
    }

    @ConfigurationProperties("outbound.recovery.effect-gate")
    public static class Settings {
        private boolean enabled = false;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
