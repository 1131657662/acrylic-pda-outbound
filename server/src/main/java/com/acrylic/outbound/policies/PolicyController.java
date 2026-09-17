package com.acrylic.outbound.policies;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.MutationAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/policies")
public class PolicyController {
    private final Access access;
    private final PolicyStore store;
    private final PolicyService service;
    private final ApiResponses responses;
    public PolicyController(Access access,PolicyStore store,PolicyService service,ApiResponses responses) {
        this.access=access;this.store=store;this.service=service;this.responses=responses;
    }
    @GetMapping("/current") @Transactional(readOnly=true)
    public ApiEnvelope<?> current(@RequestParam UUID warehouseId,HttpServletRequest request) {
        access.require("order:read",warehouseId);
        return responses.ok(new PolicyStore.Current(store.current(warehouseId)),request);
    }
    @GetMapping @Transactional(readOnly=true)
    public ApiEnvelope<?> history(@RequestParam UUID warehouseId,@RequestParam(defaultValue="9223372036854775807") long beforeVersion,
        @RequestParam(defaultValue="50") int limit,HttpServletRequest request) {
        access.require("order:read",warehouseId);return responses.ok(store.history(warehouseId,beforeVersion,limit),request);
    }
    @PostMapping
    public ApiEnvelope<?> revise(@RequestParam UUID warehouseId,@RequestHeader("Idempotency-Key") String key,
        @RequestHeader("X-Server-Recovery-Epoch") String epoch,@Valid @RequestBody PolicyService.Command body,HttpServletRequest request) {
        return responses.ok(service.revise(new MutationAccess.Context(warehouseId,epoch,key,(String)request.getAttribute("requestId")),body),request);
    }
}
