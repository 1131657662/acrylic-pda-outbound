package com.acrylic.outbound.recovery;

import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Opt-in application entry wiring for the recovery admission adapter.
 *
 * <p>With {@code outbound.recovery.admission.enabled=true} the admitted writer is the primary
 * {@link DataSource}, so every unqualified lookup (JdbcTemplate, NamedParameterJdbcTemplate, the
 * JDBC transaction manager, Spring Session JDBC) takes the external writer lease before it borrows
 * a connection. The single raw pool stays reachable only through the {@link FlywayDataSource}
 * qualifier, because migration and bootstrap are explicit independent permissions rather than
 * business writes; a caller that asks for that qualifier deliberately stands outside the gate.
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

    @Bean @Primary
    RecoveryAdmittedDataSource recoveryAdmittedDataSource(
            @Qualifier(MIGRATION_DATASOURCE) DataSource migration, Settings settings) {
        return new RecoveryAdmittedDataSource(migration, settings.location(), settings.checkpointPath());
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
