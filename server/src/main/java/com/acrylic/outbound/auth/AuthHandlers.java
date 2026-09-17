package com.acrylic.outbound.auth;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.common.ApiResponses;
import com.acrylic.outbound.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class AuthHandlers {
    private final ApiResponses responses;
    private final AuditWriter audit;
    public AuthHandlers(ApiResponses responses, AuditWriter audit) { this.responses = responses; this.audit = audit; }

    public void success(HttpServletRequest request, HttpServletResponse response, Authentication auth) throws IOException {
        SessionUser user = (SessionUser) auth.getPrincipal();
        audit.append(new AuditWriter.Event(user.id(), "WEB_LOGIN", "USER", user.id(), "{}"),
            (String) request.getAttribute("requestId"));
        responses.success(request, response, Map.of("authenticated", true));
    }

    public void failure(HttpServletRequest request, HttpServletResponse response) throws IOException {
        audit.append(new AuditWriter.Event(null, "WEB_LOGIN_FAILED", "AUTH", null, "{}"),
            (String) request.getAttribute("requestId"));
        responses.write(request, response, new BusinessException(401, "INVALID_CREDENTIALS", "账号或密码错误"));
    }

    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication auth) throws IOException {
        if (auth != null && auth.getPrincipal() instanceof SessionUser user)
            audit.append(new AuditWriter.Event(user.id(), "WEB_LOGOUT", "USER", user.id(), "{}"),
                (String) request.getAttribute("requestId"));
        responses.success(request, response, Map.of("authenticated", false));
    }
}
