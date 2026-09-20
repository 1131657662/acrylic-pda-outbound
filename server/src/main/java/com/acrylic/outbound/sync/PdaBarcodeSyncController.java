package com.acrylic.outbound.sync;

import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pda/sync")
public class PdaBarcodeSyncController {
    private final BarcodeSyncService service;
    private final ApiResponses responses;
    public PdaBarcodeSyncController(BarcodeSyncService service,ApiResponses responses) { this.service=service;this.responses=responses; }
    @GetMapping("/barcodes")
    public ApiEnvelope<?> barcodes(@RequestHeader("X-Server-Recovery-Epoch") String epoch,
        @RequestParam(defaultValue="") String cursor,@RequestParam(defaultValue="") String baselineId,
        @RequestParam(defaultValue="") String baselineCursor,@RequestParam(defaultValue="50") int limit,HttpServletRequest request) {
        return responses.ok(service.barcodes(new BarcodeSyncService.Context(epoch,cursor,baselineId,baselineCursor,limit)),request);
    }
}
