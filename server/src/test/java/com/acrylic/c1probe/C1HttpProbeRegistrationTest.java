package com.acrylic.c1probe;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.assertj.core.api.Assertions.*;

/** Detects the duplicate nested-controller registration seen in attempts557/558 without a DB run. */
class C1HttpProbeRegistrationTest {
    @Test void probeRegistersExactlyOnceWithBothHttpMappings() {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setEnvironment(new MockEnvironment().withProperty("c1.test.base", "D:/test-only-c1"));
            context.setServletContext(new MockServletContext());
            context.register(Web.class, C1HttpProbe.class);
            context.refresh();
            assertThat(context.getBeansOfType(C1HttpController.class)).hasSize(1);
            var mappings = context.getBean(RequestMappingHandlerMapping.class).getHandlerMethods();
            assertThat(mappings.values().stream().filter(method -> method.getBeanType() == C1HttpController.class)).hasSize(2);
        }
    }

    @TestConfiguration(proxyBeanMethods = false) @EnableWebMvc @EnableWebSecurity
    static class Web {
        @Bean DataSource source() { return org.mockito.Mockito.mock(DataSource.class); }
    }
}
