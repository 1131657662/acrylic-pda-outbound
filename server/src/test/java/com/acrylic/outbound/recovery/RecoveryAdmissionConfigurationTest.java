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

/** Entry wiring: the admitted writer is primary, the raw pool serves Flyway only. */
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
            assertThat(primary).isInstanceOf(RecoveryAdmittedDataSource.class);
            assertThat(migration).isInstanceOf(HikariDataSource.class).isNotSameAs(primary);
            assertThat(context.getBeansOfType(DataSource.class)).hasSize(2);
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
                "outbound.recovery.checkpoint=" + base.resolve("trusted.json"))
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("recoveryAdmittedDataSource")
                    .hasStackTraceContaining("CONTROL_FILE_MISSING");
            });
    }

    @Test void staleInstanceRefusesABorrowThroughTheEntry() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        runner.withPropertyValues(options(fixture)).run(context -> {
            assertThat(context).hasNotFailed();
            DataSource primary = context.getBean(DataSource.class);
            fixture.append(fixture.next("RECOVERING"));
            assertThat(context.getBean(DataSource.class)).isSameAs(primary);
            assertThatThrownBy(primary::getConnection).hasMessage("CONTROL_INSTANCE_STALE");
        });
    }

    private static String[] options(RecoveryControlFixture fixture) {
        return new String[] {
            "outbound.recovery.admission.enabled=true",
            "outbound.recovery.root=" + fixture.root,
            "outbound.recovery.installation-id=" + fixture.installation,
            "outbound.recovery.restore-roots=" + fixture.restoreRoot,
            "outbound.recovery.checkpoint=" + fixture.checkpoint
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
