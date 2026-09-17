package com.acrylic.outbound.orders;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.MutationAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/orders")
public class OrderMutationController {
    private final OrderService service;
    private final ApiResponses responses;
    public OrderMutationController(OrderService service,ApiResponses responses) { this.service=service;this.responses=responses; }
    @PostMapping
    public ApiEnvelope<?> create(@RequestParam UUID warehouseId,@Valid @RequestBody OrderCommands.Plan body,HttpServletRequest request) {
        return responses.ok(service.create(context(warehouseId,request),body),request);
    }
    @PostMapping("/{id}/revisions")
    public ApiEnvelope<?> revise(@RequestParam UUID warehouseId,@PathVariable UUID id,
        @Valid @RequestBody OrderCommands.Revision body,HttpServletRequest request) {
        return responses.ok(service.revise(context(warehouseId,request),id,body),request);
    }
    @PostMapping("/{id}/cancel")
    public ApiEnvelope<?> cancel(@RequestParam UUID warehouseId,@PathVariable UUID id,
        @Valid @RequestBody OrderCommands.Cancellation body,HttpServletRequest request) {
        return responses.ok(service.cancel(context(warehouseId,request),id,body),request);
    }
    private MutationAccess.Context context(UUID warehouse,HttpServletRequest request) {
        String key=request.getHeader("Idempotency-Key"),epoch=request.getHeader("X-Server-Recovery-Epoch");
        if(key==null || epoch==null) throw new BusinessException(400,"INVALID_REQUEST","缺少幂等键或恢复代次");
        return new MutationAccess.Context(warehouse,epoch,key,(String)request.getAttribute("requestId"));
    }
}
