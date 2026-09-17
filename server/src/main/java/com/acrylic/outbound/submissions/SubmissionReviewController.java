package com.acrylic.outbound.submissions;

import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class SubmissionReviewController {
    private final SubmissionReview review;private final ApiResponses responses;
    public SubmissionReviewController(SubmissionReview review,ApiResponses responses) { this.review=review;this.responses=responses; }
    @GetMapping("/orders/{order}/submission") public ApiEnvelope<?> order(@PathVariable UUID order,@RequestParam UUID warehouseId,HttpServletRequest request) {
        return responses.ok(review.forOrder(order,warehouseId),request);
    }
    @PostMapping("/submissions/{id}/review") public ApiEnvelope<?> decide(@PathVariable UUID id,@RequestBody SubmissionReview.Command body,
        @RequestHeader("X-Server-Recovery-Epoch") String epoch,HttpServletRequest request) { return responses.ok(review.decide(id,body,epoch),request); }
}
