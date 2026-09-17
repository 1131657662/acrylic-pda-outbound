package com.acrylic.outbound.inventory.master;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/delivery-mappings")
public class MappingController {
    private final MappingStore store;
    private final MappingService service;
    private final Access access;
    private final ApiResponses responses;
    public MappingController(MappingStore store, MappingService service, Access access, ApiResponses responses) {
        this.store = store; this.service = service; this.access = access; this.responses = responses;
    }
    @GetMapping
    public ApiEnvelope<?> list(@RequestParam UUID warehouseId, @RequestParam(defaultValue="") String afterCode,
        @RequestParam(defaultValue="50") int limit, HttpServletRequest request) {
        access.require("inventory:read", warehouseId);
        return responses.ok(store.list(warehouseId, afterCode, limit), request);
    }
    @GetMapping("/{code}/history")
    public ApiEnvelope<?> history(@RequestParam UUID warehouseId, @PathVariable String code,
        @RequestParam(defaultValue="9223372036854775807") long beforeVersion,
        @RequestParam(defaultValue="50") int limit, HttpServletRequest request) {
        access.require("inventory:read", warehouseId);
        return responses.ok(store.history(warehouseId, code, beforeVersion, limit), request);
    }
    @PostMapping("/revisions")
    public ApiEnvelope<?> revise(@RequestParam UUID warehouseId, @Valid @RequestBody MappingService.Revision input,
        @RequestHeader("Idempotency-Key") String key, @RequestHeader("X-Server-Recovery-Epoch") String epoch,
        HttpServletRequest request) {
        var context = new MutationAccess.Context(warehouseId, epoch, key, (String) request.getAttribute("requestId"));
        return responses.ok(Map.of("id", service.revise(context, input)), request);
    }
}
