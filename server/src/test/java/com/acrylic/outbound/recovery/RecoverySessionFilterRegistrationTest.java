package com.acrylic.outbound.recovery;

import jakarta.servlet.DispatcherType;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.session.SessionProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * S3 registration: the request entry is registered for every dispatch of a request and sits ahead of
 * the framework session filter, so the final session save is inside the request's root. Nothing is
 * registered while the entry is disabled.
 */
class RecoverySessionFilterRegistrationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(RecoveryAdmissionConfiguration.class)
        .withBean(DataSourceProperties.class, RecoverySessionFilterRegistrationTest::unreachableProperties)
        .withBean(com.acrylic.outbound.common.ApiResponses.class, () -> new com.acrylic.outbound.common.ApiResponses(
            org.mockito.Mockito.mock(com.acrylic.outbound.common.ServerState.class),
            new com.fasterxml.jackson.databind.ObjectMapper().registerModule(
                new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())));

    @Test void theRequestEntryPrecedesTheSessionFilterForRequestAsyncAndError() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        runner.withPropertyValues(options(fixture)).run(context -> {
            assertThat(context).hasNotFailed();
            var filter = context.getBean(RecoveryRequestAdmissionFilter.class);
            var registration = context.getBean("recoveryRequestAdmissionFilterRegistration", FilterRegistrationBean.class);
            assertThat(registration.getFilter()).isSameAs(filter);
            assertThat(registration.getOrder()).isEqualTo(RecoveryRequestAdmissionFilter.ORDER);
            assertThat(registration.determineDispatcherTypes())
                .containsExactlyInAnyOrder(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
            assertThat(registration.getOrder())
                .as("the root must be in place before the framework session filter saves the session")
                .isLessThan(new SessionProperties.Servlet().getFilterOrder());
            assertThat(context.getBeanNamesForType(ServletListenerRegistrationBean.class))
                .as("the destroy boundary is registered with the container").hasSize(1);
            assertThat(context.getBeanNamesForType(RecoveryOperationRegistry.class))
                .as("the request entry resolves roots from the single registry").hasSize(1);
        });
    }

    @Test void theDisabledEntryRegistersNoFilterAndNoListener() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeanNamesForType(RecoveryRequestAdmissionFilter.class)).isEmpty();
            assertThat(context.getBeanNamesForType(FilterRegistrationBean.class)).isEmpty();
            assertThat(context.getBeanNamesForType(ServletListenerRegistrationBean.class)).isEmpty();
            assertThat(context.getBeanNamesForType(DataSource.class)).isEmpty();
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
        properties.setUrl("jdbc:mysql://127.0.0.1:19330/admission-session-unit");
        properties.setUsername("admission_session_unit");
        properties.setPassword("unused");
        return properties;
    }
}
