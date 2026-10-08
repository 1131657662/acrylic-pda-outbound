package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pda/temporary-orders/handover-submissions")
@ConditionalOnProperty(prefix="outbound.temporary.handover-intake",name="enabled",havingValue="true")
public class TemporaryHandoverIntakeController {
    private final TemporaryHandoverIntake intake;private final ApiResponses responses;
    public TemporaryHandoverIntakeController(TemporaryHandoverIntake intake,ApiResponses responses) { this.intake=intake;this.responses=responses; }
    @PostMapping
    public ApiEnvelope<?> submit(@RequestHeader("X-Server-Recovery-Epoch") String epoch,@RequestBody byte[] bytes,HttpServletRequest request) {
        if(bytes.length>1048576)throw new BusinessException(413,"TEMP_HANDOVER_PAYLOAD_TOO_LARGE","交接正文超出完整传输限额，请保留原文");
        return responses.ok(intake.submit(SubmissionJson.parse(bytes),epoch),request);
    }
    @GetMapping("/{id}")
    public ApiEnvelope<?> read(@PathVariable UUID id,HttpServletRequest request) { return responses.ok(intake.read(id),request); }
}
