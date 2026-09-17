package com.acrylic.outbound.pda;

import com.acrylic.outbound.common.*;
import com.github.benmanes.caffeine.cache.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class PdaBearerFilter extends OncePerRequestFilter {
    private final PdaSessions sessions;private final ApiResponses responses;
    private final Cache<String,AtomicInteger> attempts=Caffeine.newBuilder().maximumSize(10000).expireAfterWrite(Duration.ofMinutes(15)).build();
    public PdaBearerFilter(PdaSessions sessions,ApiResponses responses) { this.sessions=sessions;this.responses=responses; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        try {
            String path=request.getRequestURI().substring(request.getContextPath().length());
            if(List.of("/api/v1/pda/pairing/challenges","/api/v1/pda/pairing/applications","/api/v1/pda/pairing/status",
                "/api/v1/pda/pairing/confirm","/api/v1/pda/pairing/cancel").contains(path)) {
                // Pairing controllers enforce their own request limits and per-operation private-key proof.
            } else if(path.startsWith("/api/v1/pda/auth/") && !path.endsWith("/logout")) {
                if(attempts.get(request.getRemoteAddr(),key->new AtomicInteger()).incrementAndGet()>300) {
                    response.setHeader("Retry-After","900");throw new BusinessException(429,"LOGIN_RATE_LIMIT","设备认证过于频繁，请稍后再试");
                }
            } else {
                String header=request.getHeader("Authorization");
                if(header==null || !header.matches("Bearer [A-Za-z0-9_-]{43}")) throw new BusinessException(401,"UNAUTHENTICATED","请使用PDA登录令牌");
                var principal=sessions.authenticate(header.substring(7));
                var context=SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of()));SecurityContextHolder.setContext(context);
            }
        } catch(BusinessException error) { responses.write(request,response,error);return; }
        chain.doFilter(request,response);
    }
}
