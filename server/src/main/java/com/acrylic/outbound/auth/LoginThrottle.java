package com.acrylic.outbound.auth;

import com.acrylic.outbound.common.ApiResponses;
import com.acrylic.outbound.common.BusinessException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.filter.OncePerRequestFilter;

public class LoginThrottle extends OncePerRequestFilter {
    private final ApiResponses responses;
    private final Cache<String, AtomicInteger> attempts = Caffeine.newBuilder()
        .maximumSize(10000).expireAfterWrite(Duration.ofMinutes(15)).build();
    public LoginThrottle(ApiResponses responses) { this.responses = responses; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws ServletException, IOException {
        if (request.getMethod().equals("POST") && request.getServletPath().equals("/api/v1/auth/web/login")) {
            int count = attempts.get(request.getRemoteAddr(), key -> new AtomicInteger()).incrementAndGet();
            if (count > 60) {
                response.setHeader("Retry-After", "900");
                responses.write(request, response, new BusinessException(429, "LOGIN_RATE_LIMIT", "登录尝试过于频繁，请稍后再试"));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
