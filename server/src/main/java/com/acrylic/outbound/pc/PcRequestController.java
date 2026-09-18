package com.acrylic.outbound.pc;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.orders.OrderQueries;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
public class PcRequestController {
    private final Access access;private final OrderQueries orders;private final PcReceipts receipts;private final ApiResponses responses;
    public PcRequestController(Access access,OrderQueries orders,PcReceipts receipts,ApiResponses responses) {
        this.access=access;this.orders=orders;this.receipts=receipts;this.responses=responses;
    }
    @GetMapping("/api/v1/orders/{id}/pc/requests/{key}")
    public ApiEnvelope<?> lookup(@PathVariable UUID id,@PathVariable String key,@RequestParam UUID warehouseId,HttpServletRequest request) {
        var actor=access.require("order:read",warehouseId);orders.head(warehouseId,id);
        return responses.ok(receipts.lookup(actor.id(),id,key),request);
    }
}
