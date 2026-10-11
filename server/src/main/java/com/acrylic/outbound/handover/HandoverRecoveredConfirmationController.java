package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Dedicated recovery purpose, separate from the ordinary TEMP confirmation and activation consumers. */
@RestController
@ConditionalOnProperty(prefix="outbound.handover.commands",name="enabled",havingValue="true")
final class HandoverRecoveredConfirmationController {
    private final ObjectProvider<HandoverRecoveredConfirmationEndpoint> endpoint;
    HandoverRecoveredConfirmationController(ObjectProvider<HandoverRecoveredConfirmationEndpoint> endpoint) { this.endpoint=endpoint; }
    @PostMapping(value="/api/v1/pda/handovers/{caseId}/recovered-confirmation",consumes=MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<byte[]> confirm(@PathVariable UUID caseId,@RequestHeader("X-Server-Recovery-Epoch") String epoch,
            HttpServletRequest request) throws IOException {
        var target=endpoint.getIfAvailable();
        if(target==null) throw new BusinessException(503,"RECOVERED_CONFIRMATION_DISABLED","恢复接收确认入口尚未开放，请保留原资料");
        byte[] raw=request.getInputStream().readNBytes(HandoverRecoveredConfirmationCommand.MAX_BYTES+1);
        byte[] response=target.confirm(caseId,epoch,raw,(String)request.getAttribute("requestId"));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).contentLength(response.length)
            .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(response);
    }
}
