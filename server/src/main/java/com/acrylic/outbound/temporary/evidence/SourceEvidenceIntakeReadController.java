package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.ApiEnvelope;
import com.acrylic.outbound.common.ApiResponses;
import com.acrylic.outbound.pda.PdaAccess;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pda/temporary-source-evidence/receipts")
public class SourceEvidenceIntakeReadController {
    private final PdaAccess access;
    private final SourceEvidenceIntakeReadService service;
    private final ApiResponses responses;

    public SourceEvidenceIntakeReadController(PdaAccess access, SourceEvidenceIntakeReadService service,
        ApiResponses responses) {
        this.access = access; this.service = service; this.responses = responses;
    }

    @GetMapping("/{originalKey}")
    public ResponseEntity<ApiEnvelope<SourceEvidenceIntakeReadService.View>> read(
        @PathVariable String originalKey, @RequestParam UUID warehouseId,
        @RequestParam UUID submissionId, @RequestParam long workRound, HttpServletRequest request) {
        access.require("pda:recover");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(responses.ok(service.read(warehouseId, submissionId, workRound, originalKey), request));
    }

    @GetMapping("/{originalKey}/original")
    public ResponseEntity<byte[]> original(@PathVariable String originalKey, @RequestParam UUID warehouseId,
        @RequestParam UUID submissionId, @RequestParam long workRound) {
        access.require("pda:recover");
        byte[] bytes = service.original(warehouseId, submissionId, workRound, originalKey);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).contentLength(bytes.length)
            .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(bytes);
    }
}
