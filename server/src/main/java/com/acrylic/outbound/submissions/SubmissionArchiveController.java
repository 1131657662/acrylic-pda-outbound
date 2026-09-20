package com.acrylic.outbound.submissions;

import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/pda/submissions")
public class SubmissionArchiveController {
    private final SubmissionArchiveService service;private final ApiResponses responses;
    public SubmissionArchiveController(SubmissionArchiveService service,ApiResponses responses) { this.service=service;this.responses=responses; }
    @PostMapping("/{id}/archive-confirmation")
    public ApiEnvelope<?> confirm(@PathVariable UUID id,@RequestHeader("X-Server-Recovery-Epoch") String epoch,
        @RequestBody byte[] bytes,HttpServletRequest request) {
        if(bytes.length>1024) throw new BusinessException(413,"ARCHIVE_REQUEST_TOO_LARGE","归档确认请求过大");
        return responses.ok(service.confirm(id,epoch,SubmissionJson.parse(bytes)),request);
    }
}
