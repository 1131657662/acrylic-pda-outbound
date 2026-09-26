package com.acrylic.outbound.temporary.command;

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
 * Command execution entry point. It only exists when {@code outbound.temporary.commands.enabled=true};
 * with the default configuration the route is absent (404) and the operation set stays empty, so this
 * class adds no production surface by default.
 *
 * <p>Authentication is the session, plus the per-kind permission checked inside {@link CommandUnitOfWork}
 * ({@code temporary_order:confirm} / {@code submission:review} / {@code temporary_order:link}). Recovery
 * epoch and actor binding come from the signed command body, not from a header. The request body is the
 * canonical command JSON and is passed through verbatim; the response is the stored original receipt bytes
 * so a replay returns the same bytes the first attempt produced.
 */
@RestController
@RequestMapping({"/api/v1/temporary-commands", "/api/v1/pda/temporary-commands"})
@ConditionalOnProperty(prefix = "outbound.temporary.commands", name = "enabled", havingValue = "true")
public class TemporaryCommandController {
    private final TemporaryCommandService service;

    public TemporaryCommandController(TemporaryCommandService service) { this.service = service; }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> execute(HttpServletRequest request) throws IOException {
        byte[] command = request.getInputStream().readAllBytes();
        byte[] original = service.execute(command, (String) request.getAttribute("requestId"));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).contentLength(original.length)
            .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(original);
    }
}
