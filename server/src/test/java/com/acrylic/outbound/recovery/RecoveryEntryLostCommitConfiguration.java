package com.acrylic.outbound.recovery;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test-only entry wiring: identical shape to {@link RecoveryAdmissionConfiguration} except that the
 * admitted writer wraps a transport that loses the commit acknowledgement after the real commit.
 * It is never part of the application; the checks install it explicitly as a source.
 *
 * <p>{@code @TestConfiguration} and the explicit condition keep it out of every other context that
 * scans {@code com.acrylic.outbound} with the test classpath present.
 */
@TestConfiguration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "outbound.recovery.test.lost-commit-entry", havingValue = "true")
@EnableConfigurationProperties(RecoveryAdmissionConfiguration.Settings.class)
public class RecoveryEntryLostCommitConfiguration {
    /** Name of the primary faulted admitted datasource. */
    public static final String ADMITTED_DATASOURCE = "lostCommitAdmittedDataSource";

    @Bean(name = "lostCommitRawDataSource")
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource lostCommitRawDataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean(name = ADMITTED_DATASOURCE) @Primary
    RecoveryAdmittedDataSource lostCommitAdmittedDataSource(
            @Qualifier("lostCommitRawDataSource") DataSource raw, RecoveryAdmissionConfiguration.Settings settings) {
        return new RecoveryAdmittedDataSource(new LostCommitDataSource(raw),
            settings.location(), settings.checkpointPath());
    }
}
