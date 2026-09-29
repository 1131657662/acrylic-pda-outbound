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
 * <p>The fault sits in the transport, under the request-root data source: the entry semantics (one
 * operation root per admission, no connection-level fallback) are the same as in production.
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
    RecoveryRequestAdmittedDataSource lostCommitAdmittedDataSource(
            @Qualifier("lostCommitRawDataSource") DataSource raw, RecoveryOperationRegistry operations) {
        // The fault sits in the transport, under the request-root data source: the entry semantics
        // (one operation root per admission, no connection-level fallback) stay those of production.
        return new RecoveryRequestAdmittedDataSource(new LostCommitDataSource(raw), operations);
    }

    @Bean
    static RecoveryOperationRegistry recoveryOperationRegistry(RecoveryAdmissionConfiguration.Settings settings) {
        return new RecoveryOperationRegistry(settings.location(), settings.checkpointPath());
    }

    /**
     * The faulted entry carries production's startup coverage as well: without the initialization
     * window the application cannot even construct its own state-reading beans, because every borrow
     * now needs an operation root (the lesson S5 pinned for the production wiring).
     *
     * <p>Both early factories are static: the guard orders every application bean after the window,
     * so a window built by an instance factory would have to instantiate the ordering configuration
     * first and would deadlock on itself.
     */
    @Bean
    static RecoveryInitializationWindow recoveryInitializationWindow(RecoveryOperationRegistry operations) {
        return new RecoveryInitializationWindow(operations);
    }

    @Bean
    static org.springframework.beans.factory.config.BeanFactoryPostProcessor recoveryStartupGuard(
            org.springframework.core.env.Environment environment) {
        return new RecoveryStartupGuard(environment);
    }
}
