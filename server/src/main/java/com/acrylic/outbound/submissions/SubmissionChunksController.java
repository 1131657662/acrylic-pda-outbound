package com.acrylic.outbound.submissions;

import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/pda/submissions/{id}")
public class SubmissionChunksController {
    private final SubmissionChunks service;private final ApiResponses responses;
    public SubmissionChunksController(SubmissionChunks service,ApiResponses responses) { this.service=service;this.responses=responses; }
    @PostMapping("/upload") public ApiEnvelope<?> start(@PathVariable UUID id,@RequestHeader("X-Server-Recovery-Epoch") String epoch,@RequestBody byte[] body,HttpServletRequest request) {
        return responses.ok(service.start(id,SubmissionJson.parse(body),epoch),request);
    }
    @PostMapping("/chunks/{index}") public ApiEnvelope<?> chunk(@PathVariable UUID id,@PathVariable int index,@RequestHeader("X-Server-Recovery-Epoch") String epoch,@RequestBody byte[] body,HttpServletRequest request) {
        return responses.ok(service.chunk(id,index,body,epoch),request);
    }
    @PostMapping("/finalize") public ApiEnvelope<?> finish(@PathVariable UUID id,@RequestHeader("X-Server-Recovery-Epoch") String epoch,HttpServletRequest request) {
        return responses.ok(service.finish(id,epoch),request);
    }
}
