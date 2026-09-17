package com.acrylic.outbound.submissions;

import com.acrylic.outbound.common.ApiEnvelope;
import com.acrylic.outbound.common.ApiResponses;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/pda")
public class SubmissionController {
    private final SubmissionService service;private final ApiResponses responses;
    public SubmissionController(SubmissionService service,ApiResponses responses) { this.service=service;this.responses=responses; }
    @PostMapping("/orders/{order}/submissions")
    public ApiEnvelope<?> submit(@PathVariable UUID order,@RequestHeader("X-Server-Recovery-Epoch") String epoch,@RequestBody byte[] bytes,HttpServletRequest request) {
        if(bytes.length>1048576) throw new com.acrylic.outbound.common.BusinessException(413,"SUBMISSION_USE_CHUNKS","请分块传输完整冻结正文");
        var body=SubmissionJson.parse(bytes);return responses.ok(service.submit(order,body,epoch),request);
    }
    @GetMapping("/submissions/{id}") public ApiEnvelope<?> read(@PathVariable UUID id,HttpServletRequest request) { return responses.ok(service.read(id),request); }
    @PostMapping("/submissions/{id}/abort") public ApiEnvelope<?> abort(@PathVariable UUID id,@RequestHeader("X-Server-Recovery-Epoch") String epoch,@RequestBody byte[] bytes,HttpServletRequest request) {
        return responses.ok(service.abort(id,SubmissionJson.parse(bytes),epoch),request);
    }
}
