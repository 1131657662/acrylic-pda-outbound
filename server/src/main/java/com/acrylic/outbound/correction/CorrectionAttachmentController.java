package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.ApiEnvelope;
import com.acrylic.outbound.common.ApiResponses;
import com.acrylic.outbound.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Opaque bounded uploads and permission-scoped downloads; Web CSRF and PDA authentication stay in their chains. */
@RestController
@RequestMapping({"/api/v1/corrections/attachments", "/api/v1/pda/corrections/attachments"})
@ConditionalOnProperty(prefix = "outbound.corrections", name = {"commands.enabled", "attachments.enabled"}, havingValue = "true")
public class CorrectionAttachmentController {
    private final CorrectionAttachmentService service;
    private final ApiResponses responses;
    private final Semaphore uploads = new Semaphore(4);

    CorrectionAttachmentController(CorrectionAttachmentService service, ApiResponses responses) {
        this.service = service; this.responses = responses;
    }

    @PutMapping(value = "/{uploadKey}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<ApiEnvelope<CorrectionAttachmentService.Metadata>> upload(@PathVariable UUID uploadKey,
            @RequestParam UUID warehouseId, @RequestHeader("X-Server-Recovery-Epoch") String epoch,
            @RequestHeader("X-Content-SHA256") String sha, HttpServletRequest request) throws IOException {
        service.authorizeUpload(warehouseId);
        if (!uploads.tryAcquire())
            throw new BusinessException(429, "ATTACHMENT_UPLOAD_BUSY", "附件上传繁忙，请保留原键稍后重试");
        try {
            byte[] bytes = CorrectionAttachmentBytes.read(request.getInputStream(), sha);
            var metadata = service.upload(warehouseId, uploadKey, epoch, sha, bytes, (String) request.getAttribute("requestId"));
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .body(responses.ok(metadata, request));
        } finally { uploads.release(); }
    }

    @GetMapping("/{attachmentId}")
    public ResponseEntity<byte[]> original(@PathVariable UUID attachmentId, @RequestParam UUID warehouseId) {
        return download(attachmentId, service.read(warehouseId, attachmentId, false));
    }

    @GetMapping("/{attachmentId}/review")
    public ResponseEntity<byte[]> review(@PathVariable UUID attachmentId, @RequestParam UUID warehouseId) {
        return download(attachmentId, service.read(warehouseId, attachmentId, true));
    }

    private ResponseEntity<byte[]> download(UUID attachment, byte[] bytes) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(bytes.length)
            .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
            .header("Content-Disposition", "attachment; filename=\"" + attachment + ".bin\"").body(bytes);
    }
}
