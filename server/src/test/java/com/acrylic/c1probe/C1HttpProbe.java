package com.acrylic.c1probe;

import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Only explicitly imported by the loopback IT. No production registration endpoint is added. */
@TestConfiguration(proxyBeanMethods = false)
public class C1HttpProbe {
    @Bean @Order(0) SecurityFilterChain c1ProbeChain(HttpSecurity http) throws Exception {
        return http.securityMatcher("/__c1/**").csrf(c -> c.disable())
            .authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
    }

    @Bean C1HttpController c1Probe(DataSource source, @Value("${c1.test.base}") String base) {
        return new C1HttpController(source, Path.of(base));
    }
}
