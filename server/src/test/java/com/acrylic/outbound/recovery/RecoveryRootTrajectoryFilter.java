package com.acrylic.outbound.recovery;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Records the container dispatch trajectory of the real HTTP run: it is registered for REQUEST,
 * ASYNC and ERROR exactly like the request entry, so every pass it sees is one pass the entry saw
 * (or refused to admit). Test-side only.
 */
final class RecoveryRootTrajectoryFilter extends OncePerRequestFilter {
    private final RecoveryOperationRegistry operations;
    private final RecoveryRootObservation observation;

    RecoveryRootTrajectoryFilter(RecoveryOperationRegistry operations, RecoveryRootObservation observation) {
        this.operations = Objects.requireNonNull(operations);
        this.observation = Objects.requireNonNull(observation);
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        record(request);
        chain.doFilter(request, response);
    }

    @Override protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        doFilterInternal(request, response, chain);
    }

    @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }
    @Override protected boolean shouldNotFilterErrorDispatch() { return false; }

    private void record(HttpServletRequest request) {
        Object held = request.getAttribute(RecoveryOperationRegistry.REQUEST_ROOT_ATTRIBUTE);
        RecoveryOperationLease root = held instanceof RecoveryOperationToken token ? operations.live(token) : null;
        observation.pass(new RecoveryRootObservation.Pass(request.getRequestURI(), request.getDispatcherType().name(),
            held != null, operations.liveRoots(), root == null ? -1 : root.openSubOperations()));
    }
}
