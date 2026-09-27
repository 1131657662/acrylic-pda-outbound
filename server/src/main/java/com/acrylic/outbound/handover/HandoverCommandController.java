package com.acrylic.outbound.handover;

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
 * Handover command entry point. It only exists when {@code outbound.handover.commands.enabled=true}; with the
 * default configuration the route is absent (404) and no handover case can be created, approved, prepared or
 * activated. Authentication is the Web session; the signed receipt keeps every capability false.
 */
@RestController
@RequestMapping("/api/v1/handover-commands")
@ConditionalOnProperty(prefix="outbound.handover.commands",name="enabled",havingValue="true")
public class HandoverCommandController {
    private final HandoverCommandService service;

    public HandoverCommandController(HandoverCommandService service) { this.service=service; }

    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> execute(HttpServletRequest request) throws IOException {
        byte[] command=request.getInputStream().readAllBytes();
        byte[] original=service.execute(command,(String)request.getAttribute("requestId"));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).contentLength(original.length)
            .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(original);
    }
}
