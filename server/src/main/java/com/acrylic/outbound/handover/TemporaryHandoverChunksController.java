package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pda/temporary-orders/handover-submissions/{id}")
@ConditionalOnProperty(prefix="outbound.temporary.handover-intake",name="enabled",havingValue="true")
public class TemporaryHandoverChunksController {
    private final TemporaryHandoverChunks chunks;private final ApiResponses responses;
    public TemporaryHandoverChunksController(TemporaryHandoverChunks chunks,ApiResponses responses) { this.chunks=chunks;this.responses=responses; }
    @PostMapping("/upload")
    public ApiEnvelope<?> start(@PathVariable UUID id,@RequestHeader("X-Server-Recovery-Epoch") String epoch,
        @RequestBody byte[] bytes,HttpServletRequest request) {
        if(bytes.length>16384)throw new BusinessException(413,"TEMP_HANDOVER_MANIFEST_TOO_LARGE","交接分块清单过大");
        return responses.ok(chunks.start(id,SubmissionJson.parse(bytes),epoch),request);
    }
    @PostMapping("/chunks/{index}")
    public ApiEnvelope<?> chunk(@PathVariable UUID id,@PathVariable int index,@RequestHeader("X-Server-Recovery-Epoch") String epoch,
        @RequestBody byte[] bytes,HttpServletRequest request) {
        if(bytes.length>262144)throw new BusinessException(413,"TEMP_HANDOVER_CHUNK_TOO_LARGE","交接分块过大");
        return responses.ok(chunks.chunk(id,index,bytes,epoch),request);
    }
    @PostMapping("/finalize")
    public ApiEnvelope<?> finish(@PathVariable UUID id,@RequestHeader("X-Server-Recovery-Epoch") String epoch,HttpServletRequest request) {
        return responses.ok(chunks.finish(id,epoch),request);
    }
}
