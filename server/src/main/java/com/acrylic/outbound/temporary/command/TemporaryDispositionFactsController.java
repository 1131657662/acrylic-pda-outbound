package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.ApiEnvelope;
import com.acrylic.outbound.common.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Same opt-in boundary as disposition writes; the default production route remains absent. */
@RestController
@RequestMapping("/api/v1/temporary-commands")
@ConditionalOnProperty(prefix="outbound.temporary.commands",name="enabled",havingValue="true")
public class TemporaryDispositionFactsController {
    private final TemporaryDispositionFacts facts;
    private final ApiResponses responses;
    public TemporaryDispositionFactsController(TemporaryDispositionFacts facts,ApiResponses responses) {
        this.facts=facts;this.responses=responses;
    }
    @GetMapping("/disposition-facts")
    public ResponseEntity<ApiEnvelope<?>> read(@RequestParam UUID warehouseId,@RequestParam UUID submissionId,
        HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responses.ok(facts.read(warehouseId,submissionId),request));
    }
}
