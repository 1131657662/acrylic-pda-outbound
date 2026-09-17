package com.acrylic.outbound.pda;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.orders.OrderQueries;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
public class AssignmentReadController {
    private final Access access;private final AssignmentStore assignments;private final OrderQueries orders;private final OfflineSigner signer;private final ApiResponses responses;
    public AssignmentReadController(Access access,AssignmentStore assignments,OrderQueries orders,OfflineSigner signer,ApiResponses responses) {
        this.access=access;this.assignments=assignments;this.orders=orders;this.signer=signer;this.responses=responses;
    }
    @GetMapping("/api/v1/orders/{id}/assignment") @Transactional(readOnly=true)
    public ApiEnvelope<?> assignment(@RequestParam UUID warehouseId,@PathVariable UUID id,HttpServletRequest request) {
        access.require("order:read",warehouseId);orders.head(warehouseId,id);var view=assignments.active(id);
        return responses.ok(new Current(view==null?null:new Summary(view.id(),view.deviceCode(),view.operatorName(),view.status(),
            view.epoch(),view.issuedAt(),view.expiresAt(),view.downloadAckAt(),view.areaNote())),request);
    }
    @GetMapping("/api/v1/pda/signing-key") public ApiEnvelope<?> key(HttpServletRequest request) { return responses.ok(signer.publicKey(),request); }
    public record Current(Summary assignment) { }
    public record Summary(String id,String deviceCode,String operatorName,String status,String epoch,String issuedAt,String expiresAt,String downloadAckAt,String areaNote) { }
}
