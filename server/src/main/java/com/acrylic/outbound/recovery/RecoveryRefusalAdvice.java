package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps a recovery refusal raised while serving a request to HTTP 503 and writes it without reading
 * the database (design §4.4: closing intent, outstanding lease, fence and missing root all answer
 * 503). The instance may be unable to serve anything at all when it refuses, so the refusal writer
 * must not depend on the very database it is refusing to use.
 *
 * <p>It is ordered first among the advices: a generic error handler that reports recovery state
 * would otherwise read the very database this refusal exists to avoid, and a refusal must never turn
 * into a failed error handler.
 *
 * <p>Refusals raised before the request reaches the framework — the admission itself — are written
 * by {@link RecoveryRequestAdmissionFilter} with the same writer.
 */
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class RecoveryRefusalAdvice {
    private final ApiResponses responses;

    public RecoveryRefusalAdvice(ApiResponses responses) {
        this.responses = Objects.requireNonNull(responses);
    }

    @ExceptionHandler(RecoveryControlException.class)
    public void refused(RecoveryControlException error, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        responses.writeWithoutState(request, response, 503, error.code(), "服务器当前不可写入，请稍后重试");
    }
}
