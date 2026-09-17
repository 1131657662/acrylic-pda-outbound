package com.acrylic.outbound.recall;

import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.parse;

@RestController @RequestMapping("/api/v1/pda")
public class PdaRecallController {
    private final RecallRequests recalls;private final RecallStops stops;private final RecoveryPackages packages;private final ApiResponses responses;
    public PdaRecallController(RecallRequests recalls,RecallStops stops,RecoveryPackages packages,ApiResponses responses) {
        this.recalls=recalls;this.stops=stops;this.packages=packages;this.responses=responses;
    }
    @GetMapping("/assignments/{id}/change-request") public ApiEnvelope<?> read(@PathVariable UUID id,HttpServletRequest request) {
        return responses.ok(recalls.pda(id),request);
    }
    @PostMapping("/recovery-packages") public ApiEnvelope<?> upload(@RequestHeader("X-Server-Recovery-Epoch") String epoch,@RequestBody byte[] body,HttpServletRequest request) {
        return responses.ok(packages.receive(parse(body),epoch),request);
    }
    @PostMapping("/change-requests/{id}/stop-ack") public ApiEnvelope<?> stop(@PathVariable UUID id,
        @RequestHeader("X-Server-Recovery-Epoch") String epoch,@RequestBody byte[] body,HttpServletRequest request) {
        return responses.ok(stops.acknowledge(id,parse(body),epoch),request);
    }
}
