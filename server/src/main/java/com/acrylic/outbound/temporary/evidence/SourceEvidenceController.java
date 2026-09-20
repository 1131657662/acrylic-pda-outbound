package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import static com.acrylic.outbound.temporary.evidence.SourceEvidenceIdentity.*;

@RestController @RequestMapping("/api/v1/temporary-source-evidence")
public class SourceEvidenceController {
    private final SourceEvidenceService service;private final ApiResponses responses;
    public SourceEvidenceController(SourceEvidenceService service,ApiResponses responses) { this.service=service;this.responses=responses; }
    @GetMapping("/{submissionId}") public ApiEnvelope<?> detail(@PathVariable UUID submissionId,
        @Valid @ModelAttribute SourceEvidenceIdentity identity,HttpServletRequest request) {
        return responses.ok(service.read(new Selection(submissionId,identity)),request);
    }
    @GetMapping("/{submissionId}/payload") public ResponseEntity<byte[]> payload(@PathVariable UUID submissionId,
        @Valid @ModelAttribute SourceEvidenceIdentity identity) {
        return bytes(service.payload(new Selection(submissionId,identity)),MediaType.APPLICATION_JSON);
    }
    @GetMapping("/{submissionId}/chunks/{index}") public ResponseEntity<byte[]> chunk(@PathVariable UUID submissionId,
        @Valid @ModelAttribute SourceEvidenceIdentity identity,@PathVariable int index) {
        return bytes(service.chunk(new Selection(submissionId,identity),index),MediaType.APPLICATION_OCTET_STREAM);
    }
    private ResponseEntity<byte[]> bytes(byte[] value,MediaType type) {
        return ResponseEntity.ok().contentType(type).contentLength(value.length).cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options","nosniff").body(value);
    }
}
