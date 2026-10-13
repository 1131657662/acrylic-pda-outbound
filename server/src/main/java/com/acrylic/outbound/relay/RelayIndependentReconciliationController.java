package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/original-relays")
public class RelayIndependentReconciliationController {
    private final RelayIndependentReconciliation service;private final ApiResponses responses;
    public RelayIndependentReconciliationController(RelayIndependentReconciliation service,ApiResponses responses) { this.service=service;this.responses=responses; }
    @GetMapping("/{id}/independent-reconciliation")
    public ResponseEntity<ApiEnvelope<?>> read(@PathVariable UUID id,@RequestParam UUID warehouseId,
        @RequestParam(defaultValue="candidates") String section,@RequestParam(defaultValue="") String cursor,
        @RequestParam(defaultValue="20") int limit,HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
            .body(responses.ok(service.read(warehouseId,id,new RelayIndependentReviewPage.Query(section,cursor,limit)),request));
    }
}
