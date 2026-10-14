package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@RestController
@RequestMapping("/api/v1/original-relays/{id}/physical")
public class RelayPhysicalController {
    private final RelayPhysicalChecks checks;private final RelayPhysicalAttachments attachments;private final ApiResponses responses;
    public RelayPhysicalController(RelayPhysicalChecks checks,RelayPhysicalAttachments attachments,ApiResponses responses) {
        this.checks=checks;this.attachments=attachments;this.responses=responses;
    }
    @PostMapping(value="/attachments",consumes="application/octet-stream")
    public ResponseEntity<ApiEnvelope<?>> upload(@PathVariable UUID id,@RequestParam UUID warehouseId,
        @RequestHeader("X-Server-Recovery-Epoch") String epoch,@RequestHeader("Idempotency-Key") String key,
        @RequestHeader("X-Evidence-SHA256") String digest,HttpServletRequest request) throws IOException {
        attachments.authorize(warehouseId);
        var bytes=request.getInputStream().readNBytes(RelayPhysicalPolicy.MAX_BYTES+1);
        return result(attachments.upload(context(warehouseId,epoch,key,request),id,digest,bytes),request);
    }
    @GetMapping("/attachments/{attachmentId}")
    public ResponseEntity<byte[]> attachment(@PathVariable UUID id,@PathVariable UUID attachmentId,@RequestParam UUID warehouseId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
            .header("Content-Disposition","attachment; filename=\""+attachmentId+".bin\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM).body(attachments.read(warehouseId,id,attachmentId));
    }
    @PostMapping(value="/checks",consumes="application/json")
    public ResponseEntity<ApiEnvelope<?>> record(@PathVariable UUID id,@RequestParam UUID warehouseId,
        @RequestHeader("X-Server-Recovery-Epoch") String epoch,@RequestHeader("Idempotency-Key") String key,
        HttpServletRequest request) throws IOException {
        attachments.authorize(warehouseId);
        var bytes=request.getInputStream().readNBytes(4*1024*1024+1);
        if(bytes.length>4*1024*1024)throw RelayPhysicalPolicy.invalid("实物请求超过4MiB上限");
        return result(checks.record(context(warehouseId,epoch,key,request),id,parse(bytes)),request);
    }
    @GetMapping("/checks/{checkId}")
    public ResponseEntity<ApiEnvelope<?>> read(@PathVariable UUID id,@PathVariable UUID checkId,@RequestParam UUID warehouseId,HttpServletRequest request) {
        return result(checks.read(warehouseId,id,checkId),request);
    }
    private RelayIntake.Context context(UUID warehouse,String epoch,String key,HttpServletRequest request) {
        return new RelayIntake.Context(warehouse,epoch,key,(String)request.getAttribute("requestId"));
    }
    private ResponseEntity<ApiEnvelope<?>> result(Object data,HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(responses.ok(data,request));
    }
}
