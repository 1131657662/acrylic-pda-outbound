package com.acrylic.outbound.rescue;

import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class RescueSupervisorController {
    private final RescueApprovals approvals;private final RescueRevocations revocations;
    private final RescueMaterials materials;private final ApiResponses responses;
    public RescueSupervisorController(RescueApprovals approvals,RescueRevocations revocations,RescueMaterials materials,ApiResponses responses) {
        this.approvals=approvals;this.revocations=revocations;this.materials=materials;this.responses=responses;
    }
    @GetMapping("/rescue-requests/{id}")
    public ResponseEntity<ApiEnvelope<?>> read(@PathVariable UUID id,@RequestParam UUID warehouseId,HttpServletRequest request) {
        return result(materials.supervisor(warehouseId,id),request);
    }
    @PostMapping("/rescue-requests/{id}/approve")
    public ResponseEntity<ApiEnvelope<?>> approve(@PathVariable UUID id,@RequestParam UUID warehouseId,
        @RequestHeader("X-Server-Recovery-Epoch") String epoch,@RequestHeader("Idempotency-Key") String key,
        @Valid @RequestBody RescueCommands.Approve body,HttpServletRequest request) {
        return result(approvals.approve(context(warehouseId,epoch,key,request),id,body),request);
    }
    @PostMapping("/rescue-grants/{id}/revoke")
    public ResponseEntity<ApiEnvelope<?>> revoke(@PathVariable UUID id,@RequestParam UUID warehouseId,
        @RequestHeader("X-Server-Recovery-Epoch") String epoch,@RequestHeader("Idempotency-Key") String key,
        @Valid @RequestBody RescueCommands.Revoke body,HttpServletRequest request) {
        return result(revocations.revoke(context(warehouseId,epoch,key,request),id,body),request);
    }
    private RescueAccess.Context context(UUID warehouse,String epoch,String key,HttpServletRequest request) {
        return new RescueAccess.Context(warehouse,epoch,key,(String)request.getAttribute("requestId"));
    }
    private ResponseEntity<ApiEnvelope<?>> result(Object data,HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
            .body(responses.ok(data,request));
    }
}
