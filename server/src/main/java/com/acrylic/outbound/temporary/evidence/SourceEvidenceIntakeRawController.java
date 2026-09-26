package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.ApiEnvelope;
import com.acrylic.outbound.common.ApiResponses;
import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.pda.PdaAccess;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** RAW acknowledges transport storage only; signed PAGE/STOP/VERIFY must still validate the evidence. */
@RestController
@RequestMapping("/api/v1/pda/temporary-source-evidence")
public class SourceEvidenceIntakeRawController {
    private static final Set<String> KINDS = Set.of(
        "LOCAL_FROZEN_PAYLOAD", "CAPTURE_RECORDS", "AUDIT_RECORDS", "AUDIT_ARCHIVE");
    private final PdaAccess access;
    private final SourceEvidenceIntakeRawObjectService service;
    private final ApiResponses responses;

    public SourceEvidenceIntakeRawController(PdaAccess access, SourceEvidenceIntakeRawObjectService service,
        ApiResponses responses) {
        this.access = access; this.service = service; this.responses = responses;
    }

    @PostMapping(value = "/raw", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<ApiEnvelope<Stored>> store(@RequestParam UUID warehouseId,
        @RequestParam UUID submissionId, @RequestParam long workRound, @RequestParam UUID openOriginalKey,
        @RequestParam UUID originalObjectId, @RequestParam String kind, @RequestParam int byteCount,
        @RequestParam String bytesSha256, @RequestHeader("X-Server-Recovery-Epoch") String epoch,
        HttpServletRequest request) throws IOException {
        access.require("pda:recover");
        validate(workRound, kind, byteCount, bytesSha256, epoch, request.getContentLengthLong());
        var stored = service.store(new SourceEvidenceIntakeRawObjectService.RawRequest(warehouseId, submissionId,
            workRound, openOriginalKey, originalObjectId, kind, epoch, byteCount, bytesSha256, request.getInputStream()));
        var data = new Stored("STORED", warehouseId, submissionId, workRound, openOriginalKey, originalObjectId,
            kind, stored.id(), stored.stored().bytes(), stored.stored().sha256(), false);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
            .body(responses.ok(data, request));
    }

    private static void validate(long round, String kind, int length, String hash, String epoch, long transportLength) {
        if (round < 1 || !KINDS.contains(kind) || length < 1 || length > 33554432
            || !hash.matches("[0-9a-f]{64}") || !epoch.matches("0|[1-9][0-9]{0,18}")
            || (transportLength >= 0 && transportLength != length))
            throw new BusinessException(400, "E2A_RAW_OBJECT_REQUEST", "原始材料种类、长度、摘要或恢复代次无效");
    }

    public record Stored(String status, UUID warehouseId, UUID submissionId, long workRound, UUID openOriginalKey,
        UUID originalObjectId, String kind, UUID objectId, int byteCount, String bytesSha256,
        boolean localDeletionAllowed) { }
}
