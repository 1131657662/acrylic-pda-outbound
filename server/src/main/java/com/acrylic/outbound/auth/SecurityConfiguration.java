package com.acrylic.outbound.auth;

import com.acrylic.outbound.common.ApiResponses;
import com.acrylic.outbound.common.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@ConditionalOnWebApplication
public class SecurityConfiguration {
    @Bean SecurityFilterChain security(HttpSecurity http, ApiResponses responses, AuthHandlers handlers) throws Exception {
        http.authorizeHttpRequests(auth -> auth.requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/web/login")
            .permitAll().anyRequest().authenticated());
        http.formLogin(form -> form.loginProcessingUrl("/api/v1/auth/web/login")
            .successHandler(handlers::success).failureHandler((req, res, error) -> handlers.failure(req, res)));
        http.logout(logout -> logout.logoutUrl("/api/v1/auth/web/logout").deleteCookies("OUTBOUND_SESSION")
            .logoutSuccessHandler(handlers::logout));
        http.exceptionHandling(errors -> errors
            .authenticationEntryPoint((req, res, error) -> responses.write(req, res,
                new BusinessException(401, "UNAUTHENTICATED", "请先登录")))
            .accessDeniedHandler((req, res, error) -> responses.write(req, res,
                new BusinessException(403, "ACCESS_DENIED", "权限不足或安全凭据失效，请刷新后重试"))));
        http.addFilterBefore(new LoginThrottle(responses), UsernamePasswordAuthenticationFilter.class);
        http.requestCache(cache -> cache.disable());
        return http.build();
    }
}
