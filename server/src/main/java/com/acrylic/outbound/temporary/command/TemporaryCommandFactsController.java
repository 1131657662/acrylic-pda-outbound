package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.ApiEnvelope;
import com.acrylic.outbound.common.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only precondition facts for the temporary command chain. It only exists when
 * {@code outbound.temporary.commands.enabled=true}; with the default configuration the route is absent (404) and
 * commands stay closed. The response carries no capability: it lets a reviewer build a command body that passes
 * the server's context comparison, nothing more. It does not create, sign or expose any receipt.
 */
@RestController
@RequestMapping("/api/v1/temporary-commands")
@ConditionalOnProperty(prefix="outbound.temporary.commands",name="enabled",havingValue="true")
public class TemporaryCommandFactsController {
    private final TemporaryCommandFacts facts;private final ApiResponses responses;

    public TemporaryCommandFactsController(TemporaryCommandFacts facts,ApiResponses responses) {
        this.facts=facts;this.responses=responses;
    }

    @GetMapping("/facts")
    public ApiEnvelope<?> read(@RequestParam UUID warehouseId,@RequestParam UUID submissionId,HttpServletRequest request) {
        return responses.ok(facts.read(warehouseId,submissionId),request);
    }
}
