package com.acrylic.outbound.handover;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Downloads remain opaque attachments under the existing command read gate. */
@RestController
@RequestMapping("/api/v1/handovers/{caseId}/attachments")
@ConditionalOnProperty(prefix = "outbound.handover.commands", name = "enabled", havingValue = "true")
public class HandoverAttachmentDownloadController {
    private final HandoverAttachmentDownload service;
    HandoverAttachmentDownloadController(HandoverAttachmentDownload service) { this.service = service; }

    @GetMapping("/{attachmentId}")
    public ResponseEntity<byte[]> original(@PathVariable UUID caseId, @PathVariable UUID attachmentId, @RequestParam UUID warehouseId) {
        var bytes = service.read(caseId, warehouseId, attachmentId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(bytes.length)
            .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
            .header("Content-Disposition", "attachment; filename=\"" + attachmentId + ".bin\"").body(bytes);
    }
}
