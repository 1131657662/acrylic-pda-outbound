package com.acrylic.outbound.handover;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Opt-in read-only discovery under the original order, without a new command surface. */
@RestController
@RequestMapping("/api/v1/handovers/cases")
@ConditionalOnProperty(prefix="outbound.handover.commands", name="enabled", havingValue="true")
public class HandoverCasePageController {
    private final HandoverCasePageService service;
    HandoverCasePageController(HandoverCasePageService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<HandoverCasePageService.Page> read(@RequestParam UUID warehouseId,
            @RequestParam UUID orderId, @RequestParam(defaultValue="20") int pageSize,
            @RequestParam(required=false) String cursor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
            .body(service.read(warehouseId, orderId, pageSize, cursor));
    }
}
