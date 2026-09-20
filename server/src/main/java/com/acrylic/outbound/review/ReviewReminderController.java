package com.acrylic.outbound.review;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.auth.IdentityStore;
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
    private final IdentityStore identities;
    public ReviewReminderController(Access access, ReviewReminderQueries queries, ApiResponses responses,IdentityStore identities) {
        this.access = access; this.queries = queries; this.responses = responses;this.identities=identities;
    }

    @GetMapping
    public ApiEnvelope<?> list(@RequestParam UUID warehouseId, @RequestParam(defaultValue = "") String cursor,
                              @RequestParam(defaultValue = "20") int limit, HttpServletRequest request) {
        var actor=access.current();var permissions=identities.permissions(actor.id());
        boolean formal=permissions.contains("submission:review"),temporary=permissions.contains("temporary_order:confirm");
        access.require(formal?"submission:review":"temporary_order:confirm", warehouseId);
        return responses.ok(queries.current(warehouseId, cursor, limit,formal,temporary), request);
    }
}
