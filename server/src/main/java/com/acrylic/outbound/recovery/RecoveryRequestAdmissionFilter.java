package com.acrylic.outbound.recovery;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Creates exactly one operation root per request and binds it to every dispatch of that request.
 *
 * <p>The root lives from the first dispatch until the container destroys the request; the dispatch
 * binding is separate and always ends with the dispatch that opened it. A re-dispatch (nested
 * ERROR, independent ERROR, ASYNC) therefore reuses the root of its request — never a second one —
 * and adds a binding for the thread that runs it. A thread that is inside such a request but was
 * never bound for the current dispatch finds no binding on the primary data source, and an ambient
 * root marker only turns that refusal into {@code RECOVERY_OPERATION_CONTEXT_LOST}; it never grants
 * access.
 *
 * <p>This filter is registered only when the admission entry is enabled, and its order must precede
 * the framework session filter so that the final session save happens inside the root.
 */
public final class RecoveryRequestAdmissionFilter extends OncePerRequestFilter {
    /**
     * Outermost position for the request dispatch: the root must cover session access, security and
     * the final session save, all of which sit later in the chain (the session filter uses the
     * framework default order {@code -2147483598}).
     */
    public static final int ORDER = org.springframework.core.Ordered.HIGHEST_PRECEDENCE;

    private final RecoveryOperationRegistry operations;
    private final com.acrylic.outbound.common.ApiResponses responses;

    public RecoveryRequestAdmissionFilter(RecoveryOperationRegistry operations,
            com.acrylic.outbound.common.ApiResponses responses) {
        this.operations = Objects.requireNonNull(operations);
        this.responses = Objects.requireNonNull(responses);
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        dispatch(request, response, chain);
    }

    /**
     * A nested ERROR dispatch runs while the request is still inside this filter, so it must reuse
     * the root as well instead of falling through to the plain chain.
     */
    @Override protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        dispatch(request, response, chain);
    }

    /** The root of an admitted request covers its ASYNC dispatch as well. */
    @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }

    /** The root of an admitted request covers its ERROR dispatch as well. */
    @Override protected boolean shouldNotFilterErrorDispatch() { return false; }

    private void dispatch(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RecoveryOperationLease root;
        try {
            root = root(request);
        } catch (RecoveryControlException refused) {
            // Admission happens before the framework, so this filter answers the refusal itself; it
            // must not read the database, which may be exactly what cannot be served right now.
            responses.writeWithoutState(request, response, 503, refused.code(), "服务器当前不可写入，请稍后重试");
            return;
        }
        Thread thread = Thread.currentThread();
        root.bindDispatch(thread);
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException failure) {
            if (!RecoveryDatabaseAdmissionException.causedBy(failure) || response.isCommitted()) throw failure;
            // Session reads/saves run outside MVC advice but inside this root. Keep the refusal
            // database-free and discard any uncommitted success body before answering.
            response.resetBuffer();
            response.setHeader("Content-Length", null);
            responses.writeWithoutState(request, response, 503, RecoveryDatabaseAdmissionException.CODE,
                "服务器当前不可写入，请稍后重试");
        } finally {
            root.unbindDispatch(thread);
            if (root.phase() == RecoveryOperationPhase.SERVING) root.enterFinalizing();
        }
    }

    private RecoveryOperationLease root(HttpServletRequest request) {
        Object held = request.getAttribute(RecoveryOperationRegistry.REQUEST_ROOT_ATTRIBUTE);
        if (held instanceof RecoveryOperationToken token) {
            RecoveryOperationLease root = operations.live(token);
            if (root == null) throw new RecoveryControlException("CONTROL_OPERATION_RELEASED");
            return root;
        }
        RecoveryOperationToken token = token(request);
        RecoveryOperationLease root = operations.admit(token);
        request.setAttribute(RecoveryOperationRegistry.REQUEST_ROOT_ATTRIBUTE, token);
        return root;
    }

    private RecoveryOperationToken token(HttpServletRequest request) {
        UUID operationId = UUID.randomUUID();
        String line = request.getMethod() + " " + request.getRequestURI() + "?"
            + (request.getQueryString() == null ? "" : request.getQueryString()) + " " + operationId;
        return new RecoveryOperationToken(operationId, RecoveryOperationKind.HTTP_REQUEST,
            operations.startup().installationId(), operations.startup().recordSha256(),
            RecoveryControlFormat.sha256(line.getBytes(StandardCharsets.UTF_8)));
    }
}
