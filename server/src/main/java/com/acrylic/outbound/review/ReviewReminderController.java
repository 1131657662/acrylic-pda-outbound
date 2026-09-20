package com.acrylic.outbound.review;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/review-reminders") @Transactional(readOnly = true)
public class ReviewReminderController {
    private final Access access;
    private final ReviewReminderQueries queries;
    private final ApiResponses responses;
    public ReviewReminderController(Access access, ReviewReminderQueries queries, ApiResponses responses) {
        this.access = access; this.queries = queries; this.responses = responses;
    }

    @GetMapping
    public ApiEnvelope<?> list(@RequestParam UUID warehouseId, @RequestParam(defaultValue = "") String cursor,
                              @RequestParam(defaultValue = "20") int limit, HttpServletRequest request) {
        access.require("submission:review", warehouseId);
        return responses.ok(queries.current(warehouseId, cursor, limit), request);
    }
}
