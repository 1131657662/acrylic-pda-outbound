package com.acrylic.outbound.audit;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/pda/audit/archives")
public class AuditArchiveController {
    private final AuditArchiveService service;private final ApiResponses responses;
    public AuditArchiveController(AuditArchiveService service,ApiResponses responses) { this.service=service;this.responses=responses; }
    @PostMapping public ApiEnvelope<?> confirm(@RequestHeader("X-Server-Recovery-Epoch") String epoch,@RequestBody byte[] bytes,HttpServletRequest request) {
        if(bytes.length>1024)throw new BusinessException(413,"AUDIT_ARCHIVE_TOO_LARGE","审计归档请求过大");
        return responses.ok(service.confirm(epoch,SubmissionJson.parse(bytes)),request);
    }
    @PostMapping("/restore") public ApiEnvelope<?> restore(@RequestHeader("X-Server-Recovery-Epoch") String epoch,@RequestBody byte[] bytes,HttpServletRequest request) {
        if(bytes.length>16384)throw new BusinessException(413,"AUDIT_ARCHIVE_TOO_LARGE","审计归档恢复请求过大");
        return responses.ok(service.restore(epoch,SubmissionJson.parse(bytes)),request);
    }
}
