package com.acrylic.outbound.audit;

import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pda/audit")
public class PdaAuditController {
    private final PdaAuditService service;private final ApiResponses responses;
    public PdaAuditController(PdaAuditService service,ApiResponses responses) { this.service=service;this.responses=responses; }
    @PostMapping("/events")
    public ApiEnvelope<?> upload(@RequestHeader("X-Server-Recovery-Epoch") String epoch,@RequestBody JsonNode body,HttpServletRequest request) {
        return responses.ok(service.upload(epoch,body),request);
    }
}
