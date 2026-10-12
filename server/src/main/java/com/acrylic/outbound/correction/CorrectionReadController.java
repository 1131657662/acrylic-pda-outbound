package com.acrylic.outbound.correction;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Command preparation and original-key recovery under the existing opt-in correction surface. */
@RestController
@RequestMapping({"/api/v1/corrections", "/api/v1/pda/corrections"})
@ConditionalOnProperty(prefix="outbound.corrections.commands", name="enabled", havingValue="true")
public class CorrectionReadController {
    private final CorrectionReadService service;
    private final CorrectionHeaderReadService headers;
    CorrectionReadController(CorrectionReadService service, CorrectionHeaderReadService headers) { this.service = service; this.headers = headers; }

    @GetMapping("/orders/{orderId}/headers")
    public ResponseEntity<byte[]> headers(@PathVariable UUID orderId, @RequestParam UUID warehouseId,
        @RequestParam(defaultValue="false") boolean review, @RequestParam(required=false) UUID before) {
        return response(json(headers.read(new CorrectionHeaderReadService.Query(warehouseId, orderId, review, before))));
    }

    @GetMapping("/orders/{orderId}/context")
    public ResponseEntity<byte[]> order(@PathVariable UUID orderId, @RequestParam UUID warehouseId) {
        return response(json(service.order(warehouseId, orderId)));
    }

    @GetMapping("/{documentId}/context")
    public ResponseEntity<byte[]> document(@PathVariable UUID documentId, @RequestParam UUID warehouseId,
            @RequestParam String commandType) {
        return response(json(service.document(warehouseId, documentId, commandType)));
    }

    @GetMapping("/commands/{commandId}")
    public ResponseEntity<byte[]> receipt(@PathVariable UUID commandId, @RequestParam UUID warehouseId,
            @RequestParam String commandType) {
        return response(service.receipt(warehouseId, commandId, commandType));
    }

    private byte[] json(JsonNode node) { return SubmissionJson.canonical(node).getBytes(StandardCharsets.UTF_8); }
    private ResponseEntity<byte[]> response(byte[] bytes) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).contentLength(bytes.length)
            .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(bytes);
    }
}
