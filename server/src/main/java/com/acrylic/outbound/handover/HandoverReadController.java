package com.acrylic.outbound.handover;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Read surface for the existing opt-in Web handover command workflow. */
@RestController
@RequestMapping("/api/v1/handovers")
@ConditionalOnProperty(prefix="outbound.handover.commands", name="enabled", havingValue="true")
public class HandoverReadController {
    private final HandoverReadService service;
    HandoverReadController(HandoverReadService service) { this.service = service; }

    @GetMapping("/scopes/{scopeId}/context")
    public ResponseEntity<byte[]> scope(@PathVariable UUID scopeId, @RequestParam UUID warehouseId) {
        return response(json(service.scope(warehouseId, scopeId)));
    }

    @GetMapping("/{caseId}/context")
    public ResponseEntity<byte[]> kase(@PathVariable UUID caseId, @RequestParam UUID warehouseId,
            @RequestParam String commandType) {
        return response(json(service.kase(warehouseId, caseId, commandType)));
    }

    @GetMapping("/{caseId}/commands/{commandId}")
    public ResponseEntity<byte[]> receipt(@PathVariable UUID caseId, @PathVariable UUID commandId,
            @RequestParam UUID warehouseId, @RequestParam String commandType) {
        return response(service.receipt(new HandoverReadService.ReceiptQuery(warehouseId, caseId, commandId, commandType)));
    }

    private byte[] json(JsonNode value) { return SubmissionJson.canonical(value).getBytes(StandardCharsets.UTF_8); }
    private ResponseEntity<byte[]> response(byte[] bytes) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).contentLength(bytes.length)
            .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(bytes);
    }
}
