package com.acrylic.outbound.handover;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** The existing default-off handover gate also controls original-device evidence intake. */
@RestController
@ConditionalOnProperty(prefix="outbound.handover.commands", name="enabled", havingValue="true")
public class HandoverStopController {
    private final HandoverStopService service;
    HandoverStopController(HandoverStopService service) { this.service = service; }

    @PostMapping(value="/api/v1/pda/handovers/{caseId}/stop-ack", consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> stop(@PathVariable UUID caseId, @RequestHeader("X-Server-Recovery-Epoch") String epoch,
            HttpServletRequest request) throws IOException {
        var command = HandoverStopCommand.parse(caseId, epoch, request.getInputStream().readNBytes(HandoverStopCommand.MAX_BYTES + 1));
        byte[] response = service.execute(command, (String) request.getAttribute("requestId"));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).contentLength(response.length)
            .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(response);
    }
}
