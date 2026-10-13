package com.acrylic.outbound.independent;

import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pda/independent-credentials")
public class IndependentCredentialController {
    private final IndependentCredentialService service;private final ApiResponses responses;
    public IndependentCredentialController(IndependentCredentialService service,ApiResponses responses) {
        this.service=service;this.responses=responses;
    }
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiEnvelope<?>> issue(@RequestHeader("X-Server-Recovery-Epoch") String epoch,
        @RequestHeader("Idempotency-Key") String key,@RequestBody byte[] body,
        HttpServletRequest request) {
        return result(service.issue(new IndependentCredentialService.Context(epoch,key,
            (String)request.getAttribute("requestId")),IndependentCredentialService.Command.read(body)),request);
    }
    @GetMapping("/{id}")
    public ResponseEntity<ApiEnvelope<?>> read(@PathVariable UUID id,HttpServletRequest request) {
        return result(service.read(id),request);
    }
    private ResponseEntity<ApiEnvelope<?>> result(Object value,HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
            .body(responses.ok(value,request));
    }
}
