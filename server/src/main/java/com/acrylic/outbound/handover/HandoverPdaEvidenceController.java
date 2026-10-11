package com.acrylic.outbound.handover;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.common.ApiEnvelope;
import com.acrylic.outbound.common.ApiResponses;
import com.acrylic.outbound.pda.AssignmentStore;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only evidence delivery for the specified receiving device and operator. */
@RestController
@RequestMapping("/api/v1/pda/handovers")
@ConditionalOnProperty(prefix="outbound.handover.commands", name="enabled", havingValue="true")
public class HandoverPdaEvidenceController {
    private final HandoverPdaEvidenceService evidence;
    private final HandoverPdaPackageService packages;
    private final HandoverPdaAssignmentService assignments;
    private final ApiResponses responses;
    private final ObjectProvider<HandoverRecoveredDownloadEndpoint> recovered;

    HandoverPdaEvidenceController(HandoverPdaEvidenceService evidence, HandoverPdaPackageService packages,
            HandoverPdaAssignmentService assignments, ApiResponses responses,ObjectProvider<HandoverRecoveredDownloadEndpoint> recovered) {
        this.evidence = evidence;
        this.packages = packages;
        this.assignments = assignments;
        this.responses = responses;
        this.recovered = recovered;
    }

    @GetMapping("/{caseId}/manifest")
    public ResponseEntity<byte[]> manifest(@PathVariable UUID caseId,HttpServletRequest request) {
        var endpoint=recovered.getIfAvailable();
        if(endpoint!=null) return response(endpoint.read(caseId,(String)request.getAttribute("requestId")).manifest());
        return response(SubmissionJson.canonical(evidence.manifest(caseId)).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/{caseId}/recovery-package")
    public ResponseEntity<byte[]> recoveryPackage(@PathVariable UUID caseId,HttpServletRequest request) {
        var endpoint=recovered.getIfAvailable();
        if(endpoint!=null) return response(endpoint.read(caseId,(String)request.getAttribute("requestId")).recoveryPackage());
        return response(packages.download(caseId));
    }

    @GetMapping("/{caseId}/assignment")
    public ResponseEntity<ApiEnvelope<AssignmentStore.View>> assignment(@PathVariable UUID caseId,
            HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options", "nosniff")
            .body(responses.ok(assignments.assignment(caseId), request));
    }

    private ResponseEntity<byte[]> response(byte[] bytes) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).contentLength(bytes.length)
            .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(bytes);
    }
}
