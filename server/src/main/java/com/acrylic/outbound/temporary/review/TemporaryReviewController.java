package com.acrylic.outbound.temporary.review;

import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/temporary-reviews")
public class TemporaryReviewController {
    private final TemporaryReviewService reviews;private final ApiResponses responses;
    public TemporaryReviewController(TemporaryReviewService reviews,ApiResponses responses) { this.reviews=reviews;this.responses=responses; }
    @GetMapping public ApiEnvelope<?> list(@RequestParam UUID warehouseId,@RequestParam(defaultValue="") String cursor,
        @RequestParam(defaultValue="20") int limit,HttpServletRequest request) {
        return responses.ok(reviews.list(warehouseId,cursor,limit),request);
    }
    @GetMapping("/{id}") public ApiEnvelope<?> detail(@PathVariable UUID id,@RequestParam UUID warehouseId,HttpServletRequest request) {
        return responses.ok(reviews.detail(id,warehouseId),request);
    }
    @GetMapping("/{id}/scans") public ApiEnvelope<?> scans(@PathVariable UUID id,@RequestParam UUID warehouseId,
        @RequestParam(defaultValue="") String cursor,@RequestParam(defaultValue="20") int limit,HttpServletRequest request) {
        return responses.ok(reviews.scans(id,warehouseId,cursor,limit),request);
    }
}
