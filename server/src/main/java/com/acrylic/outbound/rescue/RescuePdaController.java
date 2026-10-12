package com.acrylic.outbound.rescue;

import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pda/rescue-requests")
public class RescuePdaController {
    private final RescueRequests requests;private final RescueMaterials materials;private final ApiResponses responses;
    public RescuePdaController(RescueRequests requests,RescueMaterials materials,ApiResponses responses) {
        this.requests=requests;this.materials=materials;this.responses=responses;
    }
    @PostMapping
    public ResponseEntity<ApiEnvelope<?>> create(@RequestHeader("X-Server-Recovery-Epoch") String epoch,
        @RequestHeader("Idempotency-Key") String key,@Valid @RequestBody RescueCommands.Request body,HttpServletRequest request) {
        var context=new RescueAccess.Context(null,epoch,key,(String)request.getAttribute("requestId"));
        return result(requests.create(context,body),request);
    }
    @GetMapping("/{id}")
    public ResponseEntity<ApiEnvelope<?>> read(@PathVariable UUID id,HttpServletRequest request) {
        return result(materials.own(id),request);
    }
    private ResponseEntity<ApiEnvelope<?>> result(Object data,HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
            .body(responses.ok(data,request));
    }
}
