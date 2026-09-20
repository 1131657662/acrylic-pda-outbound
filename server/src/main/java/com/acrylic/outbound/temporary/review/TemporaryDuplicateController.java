package com.acrylic.outbound.temporary.review;

import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/temporary-reviews")
public class TemporaryDuplicateController {
    private final TemporaryDuplicateService candidates;private final ApiResponses responses;
    public TemporaryDuplicateController(TemporaryDuplicateService candidates,ApiResponses responses) { this.candidates=candidates;this.responses=responses; }
    @GetMapping("/{id}/duplicate-candidates")
    public ApiEnvelope<?> read(@PathVariable UUID id,@RequestParam UUID warehouseId,
        @RequestParam(required=false) LocalDate fromDate,@RequestParam(required=false) LocalDate throughDate,
        @RequestParam(defaultValue="") String cursor,@RequestParam(defaultValue="20") int limit,HttpServletRequest request) {
        return responses.ok(candidates.read(id,warehouseId,new TemporaryDuplicateModels.Filter(fromDate,throughDate,cursor,limit)),request);
    }
}
