package com.acrylic.outbound.continuation;

import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.parse;

@RestController @RequestMapping("/api/v1/pda/continuations")
public class ContinuationController {
    private final ContinuationActivation activation;private final ApiResponses responses;
    public ContinuationController(ContinuationActivation activation,ApiResponses responses) { this.activation=activation;this.responses=responses; }
    @PostMapping("/{id}/activate") public ApiEnvelope<?> activate(@PathVariable UUID id,@RequestHeader("X-Server-Recovery-Epoch") String epoch,
        @RequestBody byte[] body,HttpServletRequest request) { return responses.ok(activation.activate(id,parse(body),epoch),request); }
}
