package com.acrylic.outbound.pda;

import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/pda/assignments/{id}")
public class PreparationController {
    private final PreparationService service;private final ApiResponses responses;
    public PreparationController(PreparationService service,ApiResponses responses) { this.service=service;this.responses=responses; }
    @PostMapping("/preparation")
    public ApiEnvelope<?> prepare(@PathVariable UUID id,@RequestHeader("X-Server-Recovery-Epoch") String epoch,HttpServletRequest request) {
        return responses.ok(service.prepare(id,epoch),request);
    }
    @GetMapping("/content/{part}")
    public ResponseEntity<byte[]> content(@PathVariable UUID id,@PathVariable String part,@RequestParam UUID bundleId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType("application/gzip"))
            .body(service.content(id,bundleId.toString(),part));
    }
    @PostMapping("/download-ack")
    public ApiEnvelope<?> ack(@PathVariable UUID id,@Valid @RequestBody PreparationService.Command body,
        @RequestHeader("X-Server-Recovery-Epoch") String epoch,HttpServletRequest request) {
        return responses.ok(service.acknowledge(id,body,new PreparationService.Context(epoch,(String)request.getAttribute("requestId"))),request);
    }
}
