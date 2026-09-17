package com.acrylic.outbound.auth;

import com.acrylic.outbound.common.ApiEnvelope;
import com.acrylic.outbound.common.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    private final Access access;
    private final IdentityStore identities;
    private final ApiResponses responses;
    public AuthController(Access access, IdentityStore identities, ApiResponses responses) {
        this.access = access; this.identities = identities; this.responses = responses;
    }

    @GetMapping("/api/v1/auth/csrf")
    public ApiEnvelope<?> csrf(CsrfToken token, HttpServletRequest request) {
        return responses.ok(Map.of("headerName", token.getHeaderName(), "token", token.getToken()), request);
    }

    @GetMapping("/api/v1/me")
    public ApiEnvelope<Identity> me(HttpServletRequest request) {
        SessionUser user = access.current();
        return responses.ok(new Identity(user.id(), user.username(), user.displayName(),
            identities.permissions(user.id()), identities.warehouses(user.id())), request);
    }
    public record Identity(UUID id, String username, String displayName, List<String> permissions,
                           List<IdentityStore.Warehouse> warehouses) { }
}
