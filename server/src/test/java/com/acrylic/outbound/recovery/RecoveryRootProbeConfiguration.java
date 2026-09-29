package com.acrylic.outbound.recovery;

import jakarta.servlet.DispatcherType;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Test-side probe wiring for the S3 real-HTTP run; never part of the application. */
@TestConfiguration(proxyBeanMethods = false)
public class RecoveryRootProbeConfiguration {
    /**
     * The probe endpoints are open, and so is the container's error page: an ERROR dispatch re-enters
     * the filter chain with the error path as its request URI, and the run must observe the error
     * response rather than an authentication failure.
     */
    @Bean @Order(0) SecurityFilterChain s3ProbeChain(HttpSecurity http) throws Exception {
        return http.securityMatcher("/__s3/**", "/error").csrf(c -> c.disable())
            .authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
    }

    @Bean RecoveryRootObservation recoveryRootObservation() { return new RecoveryRootObservation(); }

    /**
     * Stands in for the missing initialization capability of the design's no-root startup list: the
     * application's own startup consumers (for example {@code JobRecoveryFence} reading
     * {@code server_state}) legitimately touch the database before any request exists. The run admits
     * one explicit INITIALIZATION root and binds it to the thread that refreshes the context. Without
     * it the context fails closed with {@code RECOVERY_OPERATION_REQUIRED} — that failure is recorded
     * as the S3 startup finding; no product bean is substituted here.
     */
    @Bean RecoveryOperationLease s3StartupRoot(RecoveryOperationRegistry operations) {
        var token = new RecoveryOperationToken(java.util.UUID.randomUUID(), RecoveryOperationKind.INITIALIZATION,
            operations.startup().installationId(), operations.startup().recordSha256(), null);
        RecoveryOperationLease root = operations.admit(token);
        root.bindDispatch(Thread.currentThread());
        return root;
    }

    /** Startup consumers of the primary data source must be created after that root exists. */
    @Bean static BeanFactoryPostProcessor s3StartupRootBeforeStartupConsumers() {
        return factory -> {
            for (String consumer : List.of("jobRecoveryFence")) {
                if (factory.containsBeanDefinition(consumer)) {
                    factory.getBeanDefinition(consumer).setDependsOn("s3StartupRoot");
                }
            }
        };
    }

    /** Registered for every dispatch, immediately after the request entry, to record the trajectory. */
    @Bean FilterRegistrationBean<RecoveryRootTrajectoryFilter> recoveryRootTrajectoryFilterRegistration(
            RecoveryOperationRegistry operations, RecoveryRootObservation observation) {
        var registration = new FilterRegistrationBean<>(new RecoveryRootTrajectoryFilter(operations, observation));
        registration.setOrder(RecoveryRequestAdmissionFilter.ORDER + 1);
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
        return registration;
    }

    @Bean static BeanPostProcessor recoverySessionSaveRecorder(ObjectProvider<RecoveryOperationRegistry> operations,
            RecoveryRootObservation observation) {
        return new RecoverySessionSaveRecorder(operations, observation);
    }

    /** Records the servlet multipart parse, which must happen inside the request's root. */
    @Bean static BeanPostProcessor recoveryMultipartParseRecorder(ObjectProvider<RecoveryOperationRegistry> operations,
            RecoveryRootObservation observation,
            @org.springframework.beans.factory.annotation.Value("${spring.servlet.multipart.location:}") String location) {
        return new RecoveryMultipartParseRecorder(operations, observation,
            location.isBlank() ? null : java.nio.file.Path.of(location));
    }
}
