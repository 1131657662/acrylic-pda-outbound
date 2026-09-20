package com.acrylic.outbound.temporary;

import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/pda/temporary-preparations")
public class TemporaryPreparationController {
    private final TemporaryPreparationService service;private final TemporaryAcknowledgementService acknowledgements;
    private final TemporaryQueries queries;private final ApiResponses responses;
    public TemporaryPreparationController(TemporaryPreparationService service,TemporaryAcknowledgementService acknowledgements,TemporaryQueries queries,ApiResponses responses) {
        this.service=service;this.acknowledgements=acknowledgements;this.queries=queries;this.responses=responses;
    }
    @GetMapping("/baseline") public ApiEnvelope<?> baseline(@RequestHeader("X-Server-Recovery-Epoch") String epoch,HttpServletRequest request) {
        return responses.ok(service.baseline(epoch),request);
    }
    @PostMapping public ApiEnvelope<?> prepare(@Valid @RequestBody TemporaryCommands.Prepare command,
        @RequestHeader("X-Server-Recovery-Epoch") String epoch,@RequestHeader("Idempotency-Key") String key,HttpServletRequest request) {
        return responses.ok(service.prepare(new TemporaryCommands.Context(epoch,key,(String)request.getAttribute("requestId")),command),request);
    }
    @GetMapping("/{id}") public ApiEnvelope<?> get(@PathVariable UUID id,HttpServletRequest request) { return responses.ok(queries.get(id),request); }
    @GetMapping("/{id}/content/{part}") public ResponseEntity<byte[]> content(@PathVariable UUID id,@PathVariable String part,@RequestParam UUID bundleId,
        @RequestHeader("X-Server-Recovery-Epoch") String epoch) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType("application/gzip"))
            .body(queries.content(new TemporaryQueries.Content(id,bundleId,part),epoch));
    }
    @PostMapping("/{id}/download-ack") public ApiEnvelope<?> ack(@PathVariable UUID id,@Valid @RequestBody TemporaryCommands.Ack command,
        @RequestHeader("X-Server-Recovery-Epoch") String epoch,@RequestHeader("Idempotency-Key") String key,HttpServletRequest request) {
        return responses.ok(acknowledgements.acknowledge(id,new TemporaryCommands.Context(epoch,key,(String)request.getAttribute("requestId")),command),request);
    }
}
