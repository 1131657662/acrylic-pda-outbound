package com.acrylic.outbound.pairing;

import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/pda/pairing")
public class PairingController {
    private final PairingChallenges challenges;private final PairingRegistration service;
    private final PairingThrottle throttle;private final ApiResponses responses;
    public PairingController(PairingChallenges challenges,PairingRegistration service,PairingThrottle throttle,ApiResponses responses) {
        this.challenges=challenges;this.service=service;this.throttle=throttle;this.responses=responses;
    }
    @PostMapping("/challenges") public ApiEnvelope<?> challenge(@Valid @RequestBody PairingCommands.ChallengeRequest body,HttpServletRequest request) {
        throttle.take("challenge",request.getRemoteAddr(),600);
        return responses.ok(challenges.create(body),request);
    }
    @PostMapping("/applications") public ApiEnvelope<?> create(@Valid @RequestBody PairingCommands.Command body,HttpServletRequest request) {
        throttle.take("create",request.getRemoteAddr(),30);
        return responses.ok(service.create(body,(String)request.getAttribute("requestId")),request);
    }
    @PostMapping("/{action:status|confirm|cancel}") public ApiEnvelope<?> operate(@PathVariable String action,
        @Valid @RequestBody PairingCommands.Command body,HttpServletRequest request) {
        throttle.take("proof",request.getRemoteAddr(),600);
        return responses.ok(service.operate(action.toUpperCase(java.util.Locale.ROOT),body,(String)request.getAttribute("requestId")),request);
    }
}
