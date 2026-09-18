package com.acrylic.outbound.pda;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.orders.OrderQueries;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/pda")
public class PdaOrdersController {
    private final PdaAccess access;private final AssignmentService service;private final AuthorizationRenewalService renewal;private final AssignmentStore store;
    private final OrderQueries orders;private final ApiResponses responses;
    public PdaOrdersController(PdaAccess access,AssignmentService service,AuthorizationRenewalService renewal,AssignmentStore store,OrderQueries orders,ApiResponses responses) {
        this.access=access;this.service=service;this.renewal=renewal;this.store=store;this.orders=orders;this.responses=responses;
    }
    @GetMapping("/orders") @Transactional(readOnly=true)
    public ApiEnvelope<?> available(@RequestParam(defaultValue="") String cursor,@RequestParam(defaultValue="50") int limit,HttpServletRequest request) {
        return responses.ok(orders.list(access.require("pda:assign").warehouseId(),new OrderQueries.Filter("READY","",cursor,limit)),request);
    }
    @PostMapping("/orders/{id}/assignments") public ApiEnvelope<?> claim(@PathVariable UUID id,@Valid @RequestBody AssignmentService.Command body,
        @RequestHeader("Idempotency-Key") String key,@RequestHeader("X-Server-Recovery-Epoch") String epoch,HttpServletRequest request) {
        return responses.ok(service.claim(new AssignmentService.Context(epoch,key,(String)request.getAttribute("requestId")),id,body),request);
    }
    @GetMapping("/assignments") @Transactional(readOnly=true)
    public ApiEnvelope<?> mine(@RequestParam(defaultValue="") String beforeId,@RequestParam(defaultValue="50") int limit,HttpServletRequest request) {
        return responses.ok(store.mine(access.current(),beforeId,limit),request);
    }
    @PostMapping({"/assignments/{id}/renewal", "/assignments/{id}/renew"})
    public ApiEnvelope<?> renew(@PathVariable UUID id,@Valid @RequestBody AuthorizationRenewalService.Command body,
        @RequestHeader("Idempotency-Key") String key,@RequestHeader("X-Server-Recovery-Epoch") String epoch,HttpServletRequest request) {
        return responses.ok(renewal.renew(new AuthorizationRenewalService.Context(epoch,key,(String)request.getAttribute("requestId")),id,body),request);
    }
}
