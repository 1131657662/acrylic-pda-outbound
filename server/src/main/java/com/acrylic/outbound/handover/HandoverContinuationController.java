package com.acrylic.outbound.handover;

import com.acrylic.outbound.submissions.SubmissionJson;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pda/handovers")
@ConditionalOnProperty(prefix="outbound.handover.commands", name="enabled", havingValue="true")
public class HandoverContinuationController {
    private final HandoverContinuationService service;
    public HandoverContinuationController(HandoverContinuationService service) { this.service=service; }

    @PostMapping("/{caseId}/continuation")
    public ResponseEntity<byte[]> continueCase(@PathVariable UUID caseId, @RequestHeader("X-Server-Recovery-Epoch") String epoch,
            @RequestBody byte[] body, HttpServletRequest request) {
        byte[] result=SubmissionJson.canonical(service.continueCase(caseId,SubmissionJson.parse(body),epoch)).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).contentLength(result.length)
            .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(result);
    }
}
