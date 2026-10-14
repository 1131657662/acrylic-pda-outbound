package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@RestController
@RequestMapping("/api/v1/original-relays/{id}/conflict-decisions")
public class RelayConflictController {
    private final RelayConflictAccess access;private final RelayConflicts conflicts;private final ApiResponses responses;
    public RelayConflictController(RelayConflictAccess access,RelayConflicts conflicts,ApiResponses responses) {
        this.access=access;this.conflicts=conflicts;this.responses=responses;
    }
    @PostMapping(consumes="application/json")
    public ResponseEntity<ApiEnvelope<?>> record(@PathVariable UUID id,@RequestParam UUID warehouseId,
        @RequestHeader("X-Server-Recovery-Epoch") String epoch,@RequestHeader("Idempotency-Key") String key,HttpServletRequest request) throws IOException {
        access.authorize(warehouseId);var bytes=request.getInputStream().readNBytes(1024*1024+1);
        if(bytes.length>1024*1024)throw RelayConflictPolicy.invalid("冲突决定请求超过1MiB上限");
        var context=new RelayIntake.Context(warehouseId,epoch,key,(String)request.getAttribute("requestId"));return result(conflicts.record(context,id,parse(bytes)),request);
    }
    @GetMapping("/{decisionId}")
    public ResponseEntity<ApiEnvelope<?>> read(@PathVariable UUID id,@PathVariable UUID decisionId,@RequestParam UUID warehouseId,HttpServletRequest request) {
        return result(conflicts.read(warehouseId,id,decisionId),request);
    }
    private ResponseEntity<ApiEnvelope<?>> result(Object data,HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(responses.ok(data,request));
    }
}
