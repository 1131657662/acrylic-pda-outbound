package com.acrylic.outbound.devices;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.MutationAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/devices")
public class DeviceController {
    private final Access access;private final DeviceStore store;private final DeviceService service;private final ApiResponses responses;
    public DeviceController(Access access,DeviceStore store,DeviceService service,ApiResponses responses) {
        this.access=access;this.store=store;this.service=service;this.responses=responses;
    }
    @GetMapping public ApiEnvelope<?> list(@RequestParam UUID warehouseId,@RequestParam(defaultValue="") String beforeId,
        @RequestParam(defaultValue="50") int limit,HttpServletRequest request) {
        access.require("device:manage",warehouseId);
        return responses.ok(store.list(warehouseId,beforeId,limit),request);
    }
    @PostMapping public ApiEnvelope<?> register(@RequestParam UUID warehouseId,@Valid @RequestBody DeviceCommands.Register body,HttpServletRequest request) {
        return responses.ok(service.register(context(warehouseId,request),body),request);
    }
    @PostMapping("/{id}/status") public ApiEnvelope<?> status(@RequestParam UUID warehouseId,@PathVariable UUID id,
        @Valid @RequestBody DeviceCommands.Status body,HttpServletRequest request) {
        return responses.ok(service.status(context(warehouseId,request),id,body),request);
    }
    @PostMapping("/{id}/temporary-order") public ApiEnvelope<?> temporaryOrder(@RequestParam UUID warehouseId,@PathVariable UUID id,
        @Valid @RequestBody DeviceCommands.TemporaryOrder body,HttpServletRequest request) {
        return responses.ok(service.temporaryOrder(context(warehouseId,request),id,body),request);
    }
    private MutationAccess.Context context(UUID warehouse,HttpServletRequest request) {
        String key=request.getHeader("Idempotency-Key"),epoch=request.getHeader("X-Server-Recovery-Epoch");
        if(key==null || epoch==null) throw new BusinessException(400,"INVALID_REQUEST","缺少幂等键或恢复代次");
        return new MutationAccess.Context(warehouse,epoch,key,(String)request.getAttribute("requestId"));
    }
}
