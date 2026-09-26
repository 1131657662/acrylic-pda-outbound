package com.acrylic.outbound.orders;

import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/orders")
public class OrderTemporaryCandidateController {
    private final OrderTemporaryCandidateService service;private final ApiResponses responses;
    public OrderTemporaryCandidateController(OrderTemporaryCandidateService service,ApiResponses responses) {
        this.service=service;this.responses=responses;
    }
    @GetMapping("/{id}/temporary-candidates")
    public ApiEnvelope<?> read(@PathVariable UUID id,@RequestParam UUID warehouseId,
        @RequestParam(required=false) LocalDate fromDate,@RequestParam(required=false) LocalDate throughDate,
        @RequestParam(defaultValue="1") int page,HttpServletRequest request) {
        return responses.ok(service.read(warehouseId,id,new OrderTemporaryCandidateModels.Filter(fromDate,throughDate,page)),request);
    }
}
