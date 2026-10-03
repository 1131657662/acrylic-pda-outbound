package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.TemporaryProtocol;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** HTTP alias for close-disposition commands with a path/body identity check. */
@RestController
@RequestMapping("/api/v1/temporary-orders")
@ConditionalOnProperty(prefix = "outbound.temporary.commands", name = "enabled", havingValue = "true")
public class TemporaryCloseDispositionController {
    private final TemporaryCommandService service;
    public TemporaryCloseDispositionController(TemporaryCommandService service) { this.service = service; }

    @PostMapping(value = "/{submissionId}/close-dispositions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> close(@PathVariable UUID submissionId, HttpServletRequest request) throws IOException {
        byte[] command = request.getInputStream().readAllBytes();
        var canonical = TemporaryProtocol.command(command);
        var body = SubmissionJson.parse(canonical);
        if (!"CLOSE_DISPOSITION".equals(body.path("commandType").asText())
            || !submissionId.toString().equals(body.path("submissionId").asText()))
            throw new com.acrylic.outbound.common.BusinessException(409, "SUBMISSION_PATH_MISMATCH", "路径与命令来源不一致");
        byte[] original = service.execute(canonical, (String) request.getAttribute("requestId"));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).contentLength(original.length)
            .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(original);
    }
}
