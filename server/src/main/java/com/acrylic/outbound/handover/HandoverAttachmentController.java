package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Web-only opaque attachments; authorization precedes bounded body allocation. */
@RestController
@RequestMapping("/api/v1/handovers/{caseId}/attachments")
@ConditionalOnProperty(prefix = "outbound.handover", name = {"commands.enabled", "fault.enabled", "attachments.enabled"}, havingValue = "true")
public class HandoverAttachmentController {
    private final HandoverAttachmentService service;
    private final ApiResponses responses;
    private final Semaphore uploads = new Semaphore(4);
    HandoverAttachmentController(HandoverAttachmentService service, ApiResponses responses) { this.service = service; this.responses = responses; }

    @PutMapping(value = "/{uploadKey}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<ApiEnvelope<HandoverAttachmentService.Metadata>> upload(@PathVariable UUID caseId, @PathVariable UUID uploadKey,
            @RequestParam UUID warehouseId, @RequestHeader("X-Server-Recovery-Epoch") String epoch,
            @RequestHeader("X-Content-SHA256") String sha, HttpServletRequest request) throws IOException {
        service.authorize(caseId, warehouseId);
        if (!uploads.tryAcquire()) throw new BusinessException(429, "ATTACHMENT_UPLOAD_BUSY", "附件上传繁忙，请保留原键稍后重试");
        try {
            var bytes = HandoverAttachmentBytes.read(request.getInputStream(), sha);
            var input = new HandoverAttachmentService.Upload(caseId, warehouseId, uploadKey, epoch, sha);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .body(responses.ok(service.upload(input, bytes, (String) request.getAttribute("requestId")), request));
        } finally { uploads.release(); }
    }

}
