package com.acrylic.outbound.recovery;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The startup guard and the initialization window: an admitted instance refuses to start while the
 * framework session writer is enabled, and otherwise creates exactly one INITIALIZATION root that
 * every application bean is ordered after, then retires it when the application is ready.
 */
class RecoveryStartupGateTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(RecoveryAdmissionConfiguration.class)
        .withBean(DataSourceProperties.class, RecoveryStartupGateTest::unreachableProperties)
        .withBean(com.acrylic.outbound.common.ApiResponses.class, () -> new com.acrylic.outbound.common.ApiResponses(
            org.mockito.Mockito.mock(com.acrylic.outbound.common.ServerState.class),
            new com.fasterxml.jackson.databind.ObjectMapper().registerModule(
                new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())));

    @Test void anAdmittedInstanceRefusesToStartWhileTheSessionWriterIsEnabled() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        runner.withPropertyValues(options(fixture, "0 * * * * *")).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                .hasStackTraceContaining("CONTROL_SESSION_CLEANUP_UNCOVERED");
        });
    }

    @Test void aMissingCleanupSettingIsTreatedAsEnabledAndFailsStartup() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        runner.withPropertyValues(withoutCleanup(options(fixture, "-"))).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                .hasStackTraceContaining("CONTROL_SESSION_CLEANUP_UNCOVERED");
        });
    }

    @Test void aMissingClosedBaselineRefusesStartupEvenWhenSessionCleanupIsOff() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        String[] values = java.util.Arrays.stream(options(fixture, "-"))
            .filter(value -> !value.startsWith("outbound.recovery.baseline=")).toArray(String[]::new);
        runner.withPropertyValues(values).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("CONTROL_BASELINE_REQUIRED");
        });
    }

    @Test void theInitializationWindowExistsBeforeApplicationBeansAndClosesWhenTheApplicationIsReady() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        StartupConsumer.observedRoots = -1;
        runner.withBean("startupConsumer", StartupConsumer.class)
            .withPropertyValues(options(fixture, "-")).run(context -> {
                assertThat(context).hasNotFailed();
                var registry = context.getBean(RecoveryOperationRegistry.class);
                assertThat(StartupConsumer.observedRoots)
                    .as("a startup consumer is created inside the initialization root").isEqualTo(1);
                assertThat(context.getBeanFactory().getBeanDefinition("startupConsumer").getDependsOn())
                    .as("the ordering is explicit, not declaration order").contains("recoveryInitializationWindow");
                var window = context.getBean(RecoveryInitializationWindow.class);
                assertThat(window.root()).isNotNull();
                assertThat(registry.liveRoots()).as("exactly one initialization root while starting").isEqualTo(1);

                context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0], context,
                    Duration.ZERO));

                assertThat(window.root()).as("the window closes when the application is ready").isNull();
                assertThat(registry.liveRoots()).as("business traffic starts with no live root").isZero();
                assertThat(context.getBean(RecoveryRequestAdmissionFilter.class)).isNotNull();
            });
    }

    private static String[] withoutCleanup(String[] options) {
        var kept = new java.util.ArrayList<String>();
        for (String option : options) if (!option.startsWith("spring.session.jdbc.cleanup-cron=")) kept.add(option);
        return kept.toArray(String[]::new);
    }

    private static String[] options(RecoveryControlFixture fixture, String cleanup) {
        return new String[] {
            "outbound.recovery.admission.enabled=true",
            "outbound.recovery.root=" + fixture.root,
            "outbound.recovery.installation-id=" + fixture.installation,
            "outbound.recovery.restore-roots=" + fixture.restoreRoot,
            "outbound.recovery.checkpoint=" + fixture.checkpoint,
            "outbound.recovery.baseline=1",
            "spring.session.jdbc.cleanup-cron=" + cleanup
        };
    }

    /** An application bean that reads the database while it is being created, like the job fence. */
    static final class StartupConsumer {
        static int observedRoots = -1;

        StartupConsumer(RecoveryOperationRegistry operations) {
            observedRoots = operations.liveRoots();
        }
    }

    private static DataSourceProperties unreachableProperties() {
        var properties = new DataSourceProperties();
        properties.setUrl("jdbc:mysql://127.0.0.1:19330/admission-startup-unit");
        properties.setUsername("admission_startup_unit");
        properties.setPassword("unused");
        return properties;
    }
}
