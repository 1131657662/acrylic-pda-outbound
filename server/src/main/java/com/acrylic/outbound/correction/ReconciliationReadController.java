package com.acrylic.outbound.correction;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Existing opt-in correction namespace supplies discrepancy facts without mutation. */
@RestController
@RequestMapping({"/api/v1/corrections/reconciliation", "/api/v1/pda/corrections/reconciliation"})
@ConditionalOnProperty(prefix="outbound.corrections.commands", name="enabled", havingValue="true")
class ReconciliationReadController {
    private final ReconciliationReadService service;
    ReconciliationReadController(ReconciliationReadService service) { this.service = service; }
    @GetMapping
    ResponseEntity<byte[]> list(@RequestParam UUID warehouseId, @RequestParam(defaultValue="false") boolean review,
        @RequestParam(required=false) UUID before, @RequestParam(required=false) UUID beforeCase,
        @RequestParam(required=false) UUID beforeReturned) {
        return response(service.list(new ReconciliationReadService.Page(warehouseId, review, before, beforeCase, beforeReturned)));
    }
    @GetMapping("/context")
    ResponseEntity<byte[]> context(@RequestParam UUID warehouseId, @RequestParam(required=false) UUID dispatchId,
        @RequestParam(required=false) UUID caseId, @RequestParam String commandType) {
        return response(service.context(warehouseId, dispatchId, caseId, commandType));
    }
    private ResponseEntity<byte[]> response(JsonNode value) {
        byte[] bytes = CorrectionReceiptCodec.bytes(value);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).contentLength(bytes.length)
            .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(bytes);
    }
}
