package com.acrylic.outbound.handover;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix="outbound.handover.commands", name="enabled", havingValue="true")
final class HandoverTemporaryConfirmationController {
    private final HandoverTemporaryConfirmationService service;
    HandoverTemporaryConfirmationController(HandoverTemporaryConfirmationService service) { this.service = service; }

    @PostMapping(value="/api/v1/pda/handovers/{caseId}/temporary-confirmation", consumes=MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<byte[]> confirm(@PathVariable UUID caseId, @RequestHeader("X-Server-Recovery-Epoch") String epoch,
            HttpServletRequest request) throws IOException {
        var command = HandoverTemporaryConfirmationCommand.parse(caseId, epoch,
            request.getInputStream().readNBytes(HandoverTemporaryConfirmationCommand.MAX_BYTES + 1));
        var bytes = service.confirm(command, (String) request.getAttribute("requestId"));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).contentLength(bytes.length)
            .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(bytes);
    }
}
