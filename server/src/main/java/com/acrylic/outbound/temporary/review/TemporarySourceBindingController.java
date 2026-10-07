package com.acrylic.outbound.temporary.review;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.PdaAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/**
 * Device-scoped read of the device's own TEMP source bindings. It exists only with the temporary command switch
 * ({@code outbound.temporary.commands.enabled=true}), which keeps the production surface unchanged; it adds no
 * write capability and no capability flag. It answers one question the device cannot answer today: has one of my
 * sources been linked to a formal order, and at which mapping version?
 */
@RestController
@RequestMapping("/api/v1/pda/temporary-source-bindings")
@ConditionalOnProperty(prefix="outbound.temporary.commands",name="enabled",havingValue="true")
public class TemporarySourceBindingController {
    private final TemporarySourceBindingQueries bindings;private final PdaAccess access;private final ApiResponses responses;

    public TemporarySourceBindingController(TemporarySourceBindingQueries bindings,PdaAccess access,ApiResponses responses) {
        this.bindings=bindings;this.access=access;this.responses=responses;
    }

    @GetMapping
    public ApiEnvelope<?> mine(@RequestParam(defaultValue="") String cursor,@RequestParam(defaultValue="20") int limit,
        @RequestParam(required=false) java.util.UUID clientOrderId,
        HttpServletRequest request) {
        var principal=access.current();
        var scope=new TemporarySourceBindingQueries.Scope(principal.deviceId(),principal.warehouseId(),clientOrderId);
        return responses.ok(bindings.mine(scope,cursor,limit),request);
    }
}
