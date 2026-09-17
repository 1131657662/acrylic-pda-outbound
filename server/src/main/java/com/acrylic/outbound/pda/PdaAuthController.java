package com.acrylic.outbound.pda;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.devices.DeviceStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/pda")
public class PdaAuthController {
    private final PdaChallenges challenges;private final PdaAuthentication auth;private final PdaAccess access;private final DeviceStore devices;private final ApiResponses responses;
    public PdaAuthController(PdaChallenges challenges,PdaAuthentication auth,PdaAccess access,DeviceStore devices,ApiResponses responses) {
        this.challenges=challenges;this.auth=auth;this.access=access;this.devices=devices;this.responses=responses;
    }
    @PostMapping("/auth/challenges") public ApiEnvelope<?> challenge(@Valid @RequestBody PdaChallenges.Request body,HttpServletRequest request) {
        return responses.ok(challenges.create(body),request);
    }
    @PostMapping("/auth/login") public ApiEnvelope<?> login(@Valid @RequestBody PdaAuthentication.Login body,HttpServletRequest request) {
        return responses.ok(auth.login(body,(String)request.getAttribute("requestId")),request);
    }
    @PostMapping("/auth/refresh") public ApiEnvelope<?> refresh(@Valid @RequestBody PdaAuthentication.Refresh body,HttpServletRequest request) {
        return responses.ok(auth.refresh(body,(String)request.getAttribute("requestId")),request);
    }
    @PostMapping("/auth/logout") public ApiEnvelope<?> logout(HttpServletRequest request) {
        auth.logout(access.current(),(String)request.getAttribute("requestId"));return responses.ok(Map.of("loggedOut",true),request);
    }
    @GetMapping("/devices/me") public ApiEnvelope<?> me(HttpServletRequest request) {
        return responses.ok(devices.find(access.current().deviceId(),false),request);
    }
}
