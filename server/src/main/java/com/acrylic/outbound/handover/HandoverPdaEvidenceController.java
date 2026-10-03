package com.acrylic.outbound.handover;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Bounded evidence manifest for the specified receiving device and operator. */
@RestController
@RequestMapping("/api/v1/pda/handovers")
@ConditionalOnProperty(prefix="outbound.handover.commands", name="enabled", havingValue="true")
public class HandoverPdaEvidenceController {
    private final HandoverPdaEvidenceService service;

    HandoverPdaEvidenceController(HandoverPdaEvidenceService service) { this.service = service; }

    @GetMapping("/{caseId}/manifest")
    public ResponseEntity<byte[]> manifest(@PathVariable UUID caseId) {
        var bytes = SubmissionJson.canonical(service.manifest(caseId)).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).contentLength(bytes.length)
            .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(bytes);
    }
}
