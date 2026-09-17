package com.acrylic.outbound.orders;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.MutationAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/orders/{id}")
public class OrderPublicationController {
    private final Access access;
    private final OrderQueries queries;
    private final OrderPublicationStore publications;
    private final OrderReleaseService service;
    private final ApiResponses responses;
    public OrderPublicationController(Access access,OrderQueries queries,OrderPublicationStore publications,OrderReleaseService service,ApiResponses responses) {
        this.access=access;this.queries=queries;this.publications=publications;this.service=service;this.responses=responses;
    }
    @PostMapping("/publish")
    public ApiEnvelope<?> publish(@RequestParam UUID warehouseId,@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,
        @RequestHeader("X-Server-Recovery-Epoch") String epoch,@Valid @RequestBody OrderReleaseService.Command body,HttpServletRequest request) {
        var context=new MutationAccess.Context(warehouseId,epoch,key,(String)request.getAttribute("requestId"));
        return responses.ok(service.publish(context,id,body),request);
    }
    @GetMapping("/publications") @Transactional(readOnly=true)
    public ApiEnvelope<?> history(@RequestParam UUID warehouseId,@PathVariable UUID id,
        @RequestParam(defaultValue="2147483648") long beforeRevision,@RequestParam(defaultValue="50") int limit,HttpServletRequest request) {
        access.require("order:read",warehouseId);queries.head(warehouseId,id);
        return responses.ok(publications.history(id,beforeRevision,limit),request);
    }
}
