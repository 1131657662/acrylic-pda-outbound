package com.acrylic.outbound.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class ApiResponses {
    private final ServerState state;
    private final ObjectMapper json;
    public ApiResponses(ServerState state, ObjectMapper json) { this.state = state; this.json = json; }

    public <T> ApiEnvelope<T> ok(T data, HttpServletRequest request) {
        return body("OK", "成功", data, request);
    }

    public <T> ApiEnvelope<T> body(String code, String message, T data, HttpServletRequest request) {
        ServerState.State current;
        try { current = state.current(); }
        // Reporting the state must never turn one failure into another: when the state cannot be
        // read — unavailable database, or an admitted instance that refuses without a root — the
        // envelope says so instead of failing inside its own error path.
        catch (RuntimeException unavailable) {
            current = new ServerState.State("unknown", "UNAVAILABLE");
        }
        return new ApiEnvelope<>(code, message, data, (String) request.getAttribute("requestId"),
            Instant.now(), current.epoch(), current.mode());
    }

    public void write(HttpServletRequest request, HttpServletResponse response, BusinessException error)
            throws IOException {
        response.setStatus(error.status());
        response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getOutputStream(), body(error.code(), error.getMessage(), null, request));
    }

    /**
     * Refusal writer: it must work while the instance cannot serve business traffic at all (fenced,
     * closing, outstanding lease, no operation root, unavailable database), so it reads <b>nothing</b>
     * from the database and never calls {@link ServerState}. The envelope keeps its shape and reports
     * the state as unknown/unavailable, which is exactly what is known on this path.
     */
    public void writeWithoutState(HttpServletRequest request, HttpServletResponse response, int status, String code,
            String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        var envelope = new ApiEnvelope<>(code, message, null, (String) request.getAttribute("requestId"),
            Instant.now(), "unknown", "UNAVAILABLE");
        json.writeValue(response.getOutputStream(), envelope);
    }

    public void success(HttpServletRequest request, HttpServletResponse response, Object data) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getOutputStream(), ok(data, request));
    }
}
