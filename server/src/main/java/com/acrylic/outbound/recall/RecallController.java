package com.acrylic.outbound.recall;

import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.parse;

@RestController @RequestMapping("/api/v1")
public class RecallController {
    private final com.acrylic.outbound.continuation.ContinuationRequests continuations;
    private final com.acrylic.outbound.continuation.ContinuationApproval approval;
    private final RecallFaultWithdrawals faultWithdrawals;
    private final RecallRequests recalls;private final RecallResolution resolution;private final RecoveryPackages packages;private final ApiResponses responses;
    public RecallController(RecallRequests recalls,RecallResolution resolution,RecoveryPackages packages,ApiResponses responses,
        com.acrylic.outbound.continuation.ContinuationRequests continuations,com.acrylic.outbound.continuation.ContinuationApproval approval,
        RecallFaultWithdrawals faultWithdrawals) {
        this.faultWithdrawals=faultWithdrawals;
        this.continuations=continuations;this.approval=approval;
        this.recalls=recalls;this.resolution=resolution;this.packages=packages;this.responses=responses;
    }
    @GetMapping("/orders/{order}/change-requests") public ApiEnvelope<?> read(@PathVariable UUID order,@RequestParam UUID warehouseId,HttpServletRequest request) {
        return responses.ok(recalls.web(order,warehouseId),request);
    }
    @PostMapping("/orders/{order}/change-requests") public ApiEnvelope<?> create(@PathVariable UUID order,@RequestParam UUID warehouseId,
        @RequestHeader("X-Server-Recovery-Epoch") String epoch,@RequestHeader("Idempotency-Key") String key,@RequestBody byte[] body,HttpServletRequest request) {
        var command=parse(body);var context=new RecallRequests.Context(warehouseId,epoch,key);
        if(command.path("type").asText().equals("FAULT_WITHDRAWAL"))return responses.ok(faultWithdrawals.withdraw(context,order,command),request);
        return responses.ok(command.path("type").asText().equals("REOPEN_WORK")?continuations.create(context,order,command):recalls.create(context,order,command),request);
    }
    @PostMapping("/change-requests/{id}/resolve") public ApiEnvelope<?> resolve(@PathVariable UUID id,@RequestParam UUID warehouseId,
        @RequestHeader("X-Server-Recovery-Epoch") String epoch,@RequestHeader("Idempotency-Key") String key,@RequestBody byte[] body,HttpServletRequest request) {
        var command=parse(body);var context=new RecallRequests.Context(warehouseId,epoch,key);
        return responses.ok(command.path("action").asText().equals("REOPEN_WORK")?approval.approve(context,id,command):resolution.resolve(context,id,command),request);
    }
    @GetMapping("/recovery-packages/{id}") public ApiEnvelope<?> recovery(@PathVariable UUID id,HttpServletRequest request) {
        return responses.ok(packages.detail(id),request);
    }
}
