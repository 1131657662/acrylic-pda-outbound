package com.acrylic.outbound.recovery;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.DispatcherType;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

/**
 * Opt-in application entry wiring for the recovery admission adapter.
 *
 * <p>With {@code outbound.recovery.admission.enabled=true} the primary {@link DataSource} is the
 * request-root data source, so every unqualified lookup (JdbcTemplate, NamedParameterJdbcTemplate,
 * the JDBC transaction manager, Spring Session JDBC) must resolve to the operation root this thread
 * is dispatching for; a thread without one is refused instead of being served. The single raw pool
 * stays reachable only through the {@link FlywayDataSource} qualifier, because migration and
 * bootstrap are explicit independent permissions rather than business writes.
 *
 * <p>The legacy connection-level adapter is no longer part of this wiring: it is an explicit
 * test/offline capability, and a static check keeps it out of the request path.
 *
 * <p>The flag is off by default: an instance without an explicit control authority must not
 * silently replace its datasource, and a missing or stale authority fails context startup instead
 * of degrading into an unguarded pool.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "outbound.recovery.admission", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RecoveryAdmissionConfiguration.Settings.class)
public class RecoveryAdmissionConfiguration {
    /** Name of the migration-only pool; it is not the primary datasource. */
    public static final String MIGRATION_DATASOURCE = "recoveryMigrationDataSource";

    @Bean(name = MIGRATION_DATASOURCE)
    @FlywayDataSource
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource recoveryMigrationDataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    /**
     * The in-memory authority for operation roots. It fails context startup when the configured
     * control authority is missing or unreadable, which is what keeps a misconfigured instance from
     * degrading into an unguarded pool.
     */
    @Bean
    RecoveryOperationRegistry recoveryOperationRegistry(Settings settings) {
        return new RecoveryOperationRegistry(settings.location(), settings.checkpointPath());
    }

    /**
     * Startup guard: refuses to start when the framework session-expiry writer is enabled (it writes
     * outside every root) and puts every application bean after the initialization window, so
     * startup database access happens inside an explicit root.
     */
    @Bean
    static BeanFactoryPostProcessor recoveryStartupGuard(Environment environment) {
        return new RecoveryStartupGuard(environment);
    }

    /**
     * The explicit initialization window: one {@code INITIALIZATION} root covering bean creation and
     * the framework's startup runners, retired when the application is ready.
     */
    @Bean
    RecoveryInitializationWindow recoveryInitializationWindow(RecoveryOperationRegistry operations) {
        return new RecoveryInitializationWindow(operations);
    }

    /** The only primary data source: every borrow must resolve to a live operation root. */
    @Bean @Primary
    RecoveryRequestAdmittedDataSource recoveryAdmittedDataSource(
            @Qualifier(MIGRATION_DATASOURCE) DataSource migration, RecoveryOperationRegistry operations) {
        return new RecoveryRequestAdmittedDataSource(migration, operations);
    }

    /**
     * The request entry: one operation root per request, bound for every dispatch of that request.
     * It is registered for REQUEST, ASYNC and ERROR so a re-dispatch reuses the root of its request,
     * and it precedes the framework session filter (order {@code -2147483598}) so the final session
     * save is covered by the root. An admission refusal is answered here (503) without reading the
     * database.
     */
    @Bean
    RecoveryRequestAdmissionFilter recoveryRequestAdmissionFilter(RecoveryOperationRegistry operations,
            com.acrylic.outbound.common.ApiResponses responses) {
        return new RecoveryRequestAdmissionFilter(operations, responses);
    }

    /** Refusals raised while serving a request answer 503 through the database-free writer. */
    @Bean
    RecoveryRefusalAdvice recoveryRefusalAdvice(com.acrylic.outbound.common.ApiResponses responses) {
        return new RecoveryRefusalAdvice(responses);
    }

    @Bean
    FilterRegistrationBean<RecoveryRequestAdmissionFilter> recoveryRequestAdmissionFilterRegistration(
            RecoveryRequestAdmissionFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(RecoveryRequestAdmissionFilter.ORDER);
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
        return registration;
    }

    /** The container's last boundary: it clears the root's bindings and retires the root. */
    @Bean
    ServletListenerRegistrationBean<RecoveryRequestRootListener> recoveryRequestRootListener(
            RecoveryOperationRegistry operations) {
        return new ServletListenerRegistrationBean<>(new RecoveryRequestRootListener(operations));
    }

    /** Trusted control identity. There is no automatic bootstrap: every value must be explicit. */
    @ConfigurationProperties("outbound.recovery")
    public static class Settings {
        private String root;
        private UUID installationId;
        private List<String> restoreRoots = List.of();
        private String checkpoint;

        RecoveryControlLocation location() {
            return new RecoveryControlLocation(Path.of(root), installationId,
                restoreRoots.stream().map(Path::of).toList());
        }

        Path checkpointPath() { return Path.of(checkpoint); }

        public String getRoot() { return root; }
        public void setRoot(String root) { this.root = root; }
        public UUID getInstallationId() { return installationId; }
        public void setInstallationId(UUID installationId) { this.installationId = installationId; }
        public List<String> getRestoreRoots() { return restoreRoots; }
        public void setRestoreRoots(List<String> restoreRoots) { this.restoreRoots = restoreRoots; }
        public String getCheckpoint() { return checkpoint; }
        public void setCheckpoint(String checkpoint) { this.checkpoint = checkpoint; }
    }
}
