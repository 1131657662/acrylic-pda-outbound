package com.acrylic.outbound.sync;

import com.acrylic.outbound.common.ApiEnvelope;
import com.acrylic.outbound.common.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pda/sync")
public class PdaSyncController {
    private final SyncService service;
    private final ApiResponses responses;

    public PdaSyncController(SyncService service, ApiResponses responses) {
        this.service = service; this.responses = responses;
    }

    @GetMapping("/orders")
    public ApiEnvelope<?> orders(@RequestHeader("X-Server-Recovery-Epoch") String epoch,
        @RequestParam(defaultValue = "") String cursor, @RequestParam(defaultValue = "") String baselineId,
        @RequestParam(defaultValue = "") String baselineCursor, @RequestParam(defaultValue = "50") int limit,
        HttpServletRequest request) {
        return responses.ok(service.orders(new SyncService.Context(epoch, cursor, baselineId, baselineCursor, limit)), request);
    }
}
