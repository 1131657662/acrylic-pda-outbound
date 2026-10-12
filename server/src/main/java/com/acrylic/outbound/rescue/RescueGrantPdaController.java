package com.acrylic.outbound.rescue;

import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** An online rescuer can provision only material for the original registered device. */
@RestController
@RequestMapping("/api/v1/pda/rescue-grants")
public class RescueGrantPdaController {
    private final RescueMaterials materials;private final ApiResponses responses;
    public RescueGrantPdaController(RescueMaterials materials,ApiResponses responses) {
        this.materials=materials;this.responses=responses;
    }
    @GetMapping("/{id}")
    public ResponseEntity<ApiEnvelope<?>> read(@PathVariable UUID id,HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
            .body(responses.ok(materials.delegated(id),request));
    }
}
