package com.acrylic.outbound.recovery;

import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.flyway.FlywayDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Entry wiring: the request-root writer is primary, the raw pool serves Flyway only. */
class RecoveryAdmissionConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(RecoveryAdmissionConfiguration.class)
        .withBean(DataSourceProperties.class, RecoveryAdmissionConfigurationTest::unreachableProperties);

    @Test void admittedWriterIsPrimaryWhileTheMigrationPoolKeepsTheFlywayQualifier() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        runner.withPropertyValues(options(fixture)).run(context -> {
            assertThat(context).hasNotFailed();
            DataSource primary = context.getBean(DataSource.class);
            DataSource migration = context.getBean(RecoveryAdmissionConfiguration.MIGRATION_DATASOURCE, DataSource.class);
            assertThat(primary).isInstanceOf(RecoveryRequestAdmittedDataSource.class);
            assertThat(migration).isInstanceOf(HikariDataSource.class).isNotSameAs(primary);
            assertThat(context.getBeansOfType(DataSource.class)).hasSize(2);
            assertThat(context.getBean(RecoveryOperationRegistry.class)).isNotNull();
            assertThat(context.getBeanNamesForType(RecoveryAdmittedDataSource.class))
                .as("the legacy connection-level adapter is not a bean of the application entry")
                .isEmpty();
            // Flyway resolves its pool by this qualifier, so it must sit on the raw pool alone.
            var definition = (AnnotatedBeanDefinition) ((BeanDefinitionRegistry) context.getBeanFactory())
                .getBeanDefinition(RecoveryAdmissionConfiguration.MIGRATION_DATASOURCE);
            assertThat(definition.getFactoryMethodMetadata().isAnnotated(FlywayDataSource.class.getName()))
                .as("Flyway resolves its pool by this qualifier").isTrue();
        });
    }

    @Test void absentFlagLeavesTheEntryWithoutAnAdmittedWriter() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeanNamesForType(RecoveryRequestAdmittedDataSource.class)).isEmpty();
            assertThat(context.getBeanNamesForType(RecoveryOperationRegistry.class)).isEmpty();
            assertThat(context.getBeanNamesForType(RecoveryAdmittedDataSource.class)).isEmpty();
            assertThat(context.getBeanNamesForType(DataSource.class)).isEmpty();
        });
    }

    @Test void missingControlAuthorityFailsStartupInsteadOfDegradingToTheRawPool() throws Exception {
        Path cases = Path.of("../.local/control-java-cases").toAbsolutePath().normalize();
        Files.createDirectories(cases);
        Path base = Files.createTempDirectory(cases, "missing-authority-");
        Path authority = Files.createDirectory(base.resolve("authority"));
        Path business = Files.createDirectory(base.resolve("business"));
        runner.withPropertyValues("outbound.recovery.admission.enabled=true",
                "outbound.recovery.root=" + authority,
                "outbound.recovery.installation-id=" + java.util.UUID.randomUUID(),
                "outbound.recovery.restore-roots=" + business,
                "outbound.recovery.checkpoint=" + base.resolve("trusted.json"),
                "spring.session.jdbc.cleanup-cron=-")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("recoveryOperationRegistry")
                    .hasStackTraceContaining("CONTROL_FILE_MISSING");
            });
    }

    @Test void borrowWithoutAnOperationRootIsRefusedWhileAStaleInstanceCannotGetOne() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        runner.withPropertyValues(options(fixture)).run(context -> {
            assertThat(context).hasNotFailed();
            var registry = context.getBean(RecoveryOperationRegistry.class);
            DataSource primary = context.getBean(DataSource.class);
            var token = new RecoveryOperationToken(java.util.UUID.randomUUID(),
                RecoveryOperationKind.HTTP_REQUEST, fixture.installation, registry.startup().recordSha256(), null);

            // The initialization window is bound to this thread while the application starts; it is
            // retired when the application is ready, which is when traffic begins.
            context.publishEvent(new org.springframework.boot.context.event.ApplicationReadyEvent(
                new org.springframework.boot.SpringApplication(), new String[0], context, java.time.Duration.ZERO));
            assertThat(registry.liveRoots()).as("business traffic starts with no live root").isZero();

            // No thread binding and no root marker: the primary refuses, it never borrows a
            // connection-level lease for this thread.
            assertThatThrownBy(primary::getConnection).hasMessage("RECOVERY_OPERATION_REQUIRED");
            assertThat(registry.liveRoots()).isZero();

            // An authority that moved on since startup cannot hand out an operation root either.
            fixture.append(fixture.next("RECOVERING"));
            assertThatThrownBy(() -> RecoveryControlLock.locked(fixture.root, () -> registry.register(token)))
                .hasMessage("CONTROL_INSTANCE_STALE");
            assertThat(registry.liveRoots()).isZero();
        });
    }

    private static String[] options(RecoveryControlFixture fixture) {
        return new String[] {
            "outbound.recovery.admission.enabled=true",
            "outbound.recovery.root=" + fixture.root,
            "outbound.recovery.installation-id=" + fixture.installation,
            "outbound.recovery.restore-roots=" + fixture.restoreRoot,
            "outbound.recovery.checkpoint=" + fixture.checkpoint,
            "spring.session.jdbc.cleanup-cron=-"
        };
    }

    private static DataSourceProperties unreachableProperties() {
        var properties = new DataSourceProperties();
        properties.setUrl("jdbc:mysql://127.0.0.1:19330/admission-entry-unit");
        properties.setUsername("admission_entry_unit");
        properties.setPassword("unused");
        return properties;
    }
}
