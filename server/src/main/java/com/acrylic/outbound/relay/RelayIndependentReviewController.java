package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/original-relays")
public class RelayIndependentReviewController {
    private final RelayIndependentReview review;private final ApiResponses responses;
    public RelayIndependentReviewController(RelayIndependentReview review,ApiResponses responses) { this.review=review;this.responses=responses; }
    @GetMapping("/{id}/independent-review")
    public ResponseEntity<ApiEnvelope<?>> read(@PathVariable UUID id,@RequestParam UUID warehouseId,HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
            .body(responses.ok(review.read(warehouseId,id),request));
    }
}
