package com.acrylic.outbound.recovery;

import java.nio.file.Path;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies the real P5 authority the effect gate attaches to when cross-process fencing is opted in
 * (design rows 8–9). It is off by default and independent of the admission switch: an installation
 * without an explicitly supplied P5 identity must not acquire a lease root, a process record or a
 * fence channel by accident.
 *
 * <p>Every value is explicit — P5 root, closed-authority path, the pinned P3 identity digest and the
 * provisioned {@code lease-root.json} digest — and the anchor secret comes from the effect-gate
 * settings, never from the writable tree. A missing, stale or mismatched value fails context startup
 * with the control code of the check that refused.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "outbound.recovery.lease", name = "enabled", havingValue = "true")
@EnableConfigurationProperties({RecoveryLeaseConfiguration.Settings.class, RecoveryAdmissionConfiguration.Settings.class,
    RecoveryEffectConfiguration.Settings.class})
public class RecoveryLeaseConfiguration {
    /**
     * The process-wide P5 lifecycle: identity verification, fence channel, orphan scan, process
     * record and lifecycle lock. Spring calls {@link RecoveryLeaseLifecycle#close()} at shutdown,
     * which retires the lock; an unknown outcome fences through the authority instead.
     */
    @Bean
    RecoveryLeaseLifecycle recoveryLeaseLifecycle(Settings lease, RecoveryAdmissionConfiguration.Settings admission,
            RecoveryEffectConfiguration.Settings effects) {
        return RecoveryLeaseLifecycle.open(lease.binding(admission), lease.getMarkerSha256(), effects.getAnchor());
    }

    /** The authority the gate attaches to; the lifecycle remains its single owner. */
    @Bean
    RecoveryFenceAuthority recoveryFenceAuthority(RecoveryLeaseLifecycle lifecycle) {
        return lifecycle.authority();
    }

    /** Trusted P5 binding. There is no automatic provision: the supplier creates the tree first. */
    @ConfigurationProperties("outbound.recovery.lease")
    public static class Settings {
        private String root;
        private String closedAuthority;
        private String identitySha256;
        private String markerSha256;

        RecoveryLeaseRoots.Binding binding(RecoveryAdmissionConfiguration.Settings admission) {
            Objects.requireNonNull(admission, "admission settings");
            if (root == null || root.isBlank() || closedAuthority == null || closedAuthority.isBlank()
                    || identitySha256 == null || identitySha256.isBlank()) {
                throw new RecoveryControlException("RECOVERY_LEASE_ROOT_UNBOUND", null);
            }
            return new RecoveryLeaseRoots.Binding(admission.location(), Path.of(root),
                Path.of(closedAuthority), identitySha256);
        }

        public String getRoot() { return root; }
        public void setRoot(String root) { this.root = root; }
        public String getClosedAuthority() { return closedAuthority; }
        public void setClosedAuthority(String closedAuthority) { this.closedAuthority = closedAuthority; }
        public String getIdentitySha256() { return identitySha256; }
        public void setIdentitySha256(String identitySha256) { this.identitySha256 = identitySha256; }
        public String getMarkerSha256() { return markerSha256; }
        public void setMarkerSha256(String markerSha256) { this.markerSha256 = markerSha256; }
    }
}
