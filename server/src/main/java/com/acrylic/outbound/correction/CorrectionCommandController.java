package com.acrylic.outbound.correction;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Correction command entry point. It only exists when {@code outbound.corrections.commands.enabled=true}; with the
 * default configuration the route is absent (404) and no correction document can be created, approved or withdrawn.
 * Authentication is the Web session (or a PDA Bearer); the signed receipt keeps every capability false.
 */
@RestController
@RequestMapping("/api/v1/corrections")
@ConditionalOnProperty(prefix="outbound.corrections.commands",name="enabled",havingValue="true")
public class CorrectionCommandController {
    private final CorrectionCommandService service;

    public CorrectionCommandController(CorrectionCommandService service) { this.service=service; }

    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> execute(HttpServletRequest request) throws IOException {
        byte[] command=request.getInputStream().readAllBytes();
        byte[] original=service.execute(command,(String)request.getAttribute("requestId"));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).contentLength(original.length)
            .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(original);
    }
}
