package com.acrylic.outbound.inventory.master;

import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory/imports/{id}")
public class PublicationController {
    private final PreviewViews views;
    private final PublicationService service;
    private final ApiResponses responses;
    public PublicationController(PreviewViews views, PublicationService service, ApiResponses responses) {
        this.views = views; this.service = service; this.responses = responses;
    }
    @GetMapping("/diff")
    public ApiEnvelope<?> summary(@PathVariable UUID id, @RequestParam UUID warehouseId, HttpServletRequest request) {
        return responses.ok(views.summary(warehouseId, id), request);
    }
    @GetMapping("/diff/{kind}")
    public ApiEnvelope<?> page(@PathVariable UUID id, @PathVariable String kind, @RequestParam UUID warehouseId,
        @RequestParam(defaultValue="") String after, @RequestParam(defaultValue="50") int limit,
        @RequestParam String expectedSnapshot, @RequestParam String expectedMappingRevision, HttpServletRequest request) {
        return responses.ok(views.page(warehouseId, id, new PreviewViews.Query(kind, after, limit, expectedSnapshot, expectedMappingRevision)), request);
    }
    @PostMapping("/publish")
    public ApiEnvelope<?> publish(@PathVariable UUID id, @RequestParam UUID warehouseId,
        @Valid @RequestBody PublicationService.Decisions decisions, @RequestHeader("Idempotency-Key") String key,
        @RequestHeader("X-Server-Recovery-Epoch") String epoch, HttpServletRequest request) throws IOException {
        var context = new MutationAccess.Context(warehouseId, epoch, key, (String) request.getAttribute("requestId"));
        return responses.ok(service.publish(context, new PublicationService.Command(id, decisions)), request);
    }
}
