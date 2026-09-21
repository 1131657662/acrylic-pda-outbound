package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/temporary-command-receipts","/api/v1/pda/temporary-command-receipts"})
public class CommandReadController {
    private final CommandReadService service;private final ApiResponses responses;
    public CommandReadController(CommandReadService service,ApiResponses responses) { this.service=service;this.responses=responses; }
    @GetMapping("/{commandId}") public ResponseEntity<ApiEnvelope<CommandReadService.View>> read(
        @PathVariable String commandId,@RequestParam UUID warehouseId,HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responses.ok(service.read(warehouseId,commandId),request));
    }
    @GetMapping("/{commandId}/original") public ResponseEntity<byte[]> original(@PathVariable String commandId,@RequestParam UUID warehouseId) {
        byte[] bytes=service.original(warehouseId,commandId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).contentLength(bytes.length).cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options","nosniff").body(bytes);
    }
}
