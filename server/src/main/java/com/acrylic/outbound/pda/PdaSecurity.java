package com.acrylic.outbound.pda;

import com.acrylic.outbound.common.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration @ConditionalOnWebApplication
public class PdaSecurity {
    @Bean @Order(1) SecurityFilterChain pdaFilterChain(HttpSecurity http,PdaSessions sessions,ApiResponses responses) throws Exception {
        http.securityMatcher("/api/v1/pda/**").sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.csrf(c->c.disable()).requestCache(c->c.disable()).formLogin(c->c.disable()).logout(c->c.disable());
        http.authorizeHttpRequests(a->a.requestMatchers("/api/v1/pda/auth/challenges","/api/v1/pda/auth/login","/api/v1/pda/auth/refresh",
            "/api/v1/pda/pairing/challenges","/api/v1/pda/pairing/applications","/api/v1/pda/pairing/status",
            "/api/v1/pda/pairing/confirm","/api/v1/pda/pairing/cancel")
            .permitAll().anyRequest().authenticated());
        http.exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->responses.write(req,res,new BusinessException(401,"UNAUTHENTICATED","请登录PDA"))));
        http.addFilterBefore(new PdaBearerFilter(sessions,responses),UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
