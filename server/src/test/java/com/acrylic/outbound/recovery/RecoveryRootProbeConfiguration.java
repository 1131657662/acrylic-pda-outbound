package com.acrylic.outbound.recovery;

import jakarta.servlet.DispatcherType;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
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
