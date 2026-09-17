package com.acrylic.outbound.pairing;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.MutationAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/device-pairings")
public class PairingAdminController {
    private final PairingAdministration service;private final PairingThrottle throttle;
    private final Access access;private final ApiResponses responses;
    public PairingAdminController(PairingAdministration service,PairingThrottle throttle,Access access,ApiResponses responses) {
        this.service=service;this.throttle=throttle;this.access=access;this.responses=responses;
    }
    @GetMapping public ApiEnvelope<?> lookup(@RequestParam UUID warehouseId,@RequestParam String code,HttpServletRequest request) {
        var actor=access.require("device:manage",warehouseId);throttle.take("lookup",actor.id().toString(),30);
        throttle.take("lookup-ip",request.getRemoteAddr(),120);
        return responses.ok(service.lookup(warehouseId,code),request);
    }
    @PostMapping("/{id}/approval") public ApiEnvelope<?> approve(@RequestParam UUID warehouseId,@PathVariable UUID id,
        @RequestHeader("Idempotency-Key") String key,@RequestHeader("X-Server-Recovery-Epoch") String epoch,
        @Valid @RequestBody PairingCommands.ApprovalRequest body,HttpServletRequest request) {
        var context=new MutationAccess.Context(warehouseId,epoch,key,(String)request.getAttribute("requestId"));
        return responses.ok(service.approve(context,id,body),request);
    }
    @GetMapping("/{id}") public ApiEnvelope<?> current(@RequestParam UUID warehouseId,@PathVariable UUID id,HttpServletRequest request) {
        return responses.ok(service.current(warehouseId,id),request);
    }
}
