package com.acrylic.outbound.continuation;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.recall.RecallRequests;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.parse;

@RestController @RequestMapping("/api/v1/recovery-packages/{id}/disposition-evidence")
public class DispositionController {
    private final DispositionApprovals approvals;private final ApiResponses responses;
    public DispositionController(DispositionApprovals approvals,ApiResponses responses) { this.approvals=approvals;this.responses=responses; }
    @GetMapping public ApiEnvelope<?> read(@PathVariable UUID id,@RequestParam UUID warehouseId,HttpServletRequest request) {
        return responses.ok(approvals.read(warehouseId,id),request);
    }
    @PostMapping public ApiEnvelope<?> approve(@PathVariable UUID id,@RequestParam UUID warehouseId,
        @RequestHeader("X-Server-Recovery-Epoch") String epoch,@RequestHeader("Idempotency-Key") String key,@RequestBody byte[] body,HttpServletRequest request) {
        return responses.ok(approvals.approve(new RecallRequests.Context(warehouseId,epoch,key),id,parse(body)),request);
    }
}
