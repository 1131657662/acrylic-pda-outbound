package com.acrylic.outbound.recovery;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequestEvent;
import java.sql.Connection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.WebUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * S3: one operation root per request, a binding per dispatch, no second root on a re-dispatch, and
 * a thread without a binding never gets a connection — even while its request is being served.
 */
class RecoveryRequestAdmissionFilterTest {
    @AfterEach void clearAmbientRequest() { RequestContextHolder.resetRequestAttributes(); }

    @Test void oneRootPerRequestIsBoundForTheDispatchAndRetiredAtRequestDestroy() throws Exception {
        var f = fixture();
        var request = new MockHttpServletRequest("POST", "/api/outbound/things");
        var observed = new AtomicReference<Object>();

        f.filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            observed.set(f.registry.bound(Thread.currentThread()));
            borrows(f.adapter);
        });

        var token = token(request);
        assertThat(token).isNotNull();
        assertThat(observed.get()).isEqualTo(token);
        assertThat(f.registry.liveRoots()).as("exactly one root for one request").isEqualTo(1);
        assertThat(f.registry.bound(Thread.currentThread())).as("the dispatch binding ends with the dispatch").isNull();
        assertThat(f.registry.live(token).phase()).as("retained work after the chain stays covered")
            .isEqualTo(RecoveryOperationPhase.FINALIZING);
        assertThatThrownBy(f.adapter::getConnection)
            .as("outside a dispatch scope the same thread is refused").hasMessage("RECOVERY_OPERATION_REQUIRED");

        f.destroy(request);
        assertThat(f.registry.liveRoots()).isZero();
        assertThat(f.registry.live(token)).isNull();
    }

    @Test void nestedErrorDispatchReusesTheSameRootAndKeepsTheOuterBinding() throws Exception {
        var f = fixture();
        var request = new MockHttpServletRequest("GET", "/api/outbound/broken");
        var innerBound = new AtomicReference<Object>();

        f.filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            var outer = f.registry.bound(Thread.currentThread());
            errorDispatch(request, f.filter, (inner, response) -> innerBound.set(f.registry.bound(Thread.currentThread())));
            assertThat(f.registry.bound(Thread.currentThread()))
                .as("the nested dispatch does not clear the outer scope").isEqualTo(outer);
        });

        var token = token(request);
        assertThat(innerBound.get()).as("the nested ERROR dispatch binds its own thread").isEqualTo(token);
        assertThat(f.registry.liveRoots()).as("a re-dispatch never creates a second root").isEqualTo(1);
        assertThat(f.registry.bound(Thread.currentThread())).isNull();
        f.destroy(request);
        assertThat(f.registry.liveRoots()).isZero();
    }

    @Test void independentErrorDispatchAfterTheChainReusesTheRootInsteadOfAdmittingAgain() throws Exception {
        var f = fixture();
        var request = new MockHttpServletRequest("GET", "/api/outbound/broken");
        f.filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });
        var token = token(request);
        var rebound = new AtomicReference<Object>();

        // The container runs the ERROR dispatch as a separate pass: the "already filtered" marker of
        // the first dispatch is gone, but the request still carries its root.
        errorDispatch(request, f.filter, (inner, response) -> rebound.set(f.registry.bound(Thread.currentThread())));

        assertThat(rebound.get()).isEqualTo(token);
        assertThat(f.registry.liveRoots()).isEqualTo(1);
        f.destroy(request);
        assertThat(f.registry.liveRoots()).isZero();
    }

    @Test void asyncDispatchRebindsItsOwnThreadWhileTheRootIsInItsFinalizingPhase() throws Exception {
        var f = fixture();
        var request = new MockHttpServletRequest("GET", "/api/outbound/async");
        f.filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });
        var token = token(request);
        var asyncBound = new AtomicReference<Object>();

        request.setDispatcherType(DispatcherType.ASYNC);
        f.filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            asyncBound.set(f.registry.bound(Thread.currentThread()));
            borrows(f.adapter);
        });

        assertThat(asyncBound.get()).isEqualTo(token);
        assertThat(f.registry.liveRoots()).isEqualTo(1);
        f.destroy(request);
        assertThat(f.registry.liveRoots()).isZero();
    }

    @Test void aThreadWithoutADispatchBindingIsRefusedWhileTheRequestIsServed() throws Exception {
        var f = fixture();
        var request = new MockHttpServletRequest("GET", "/api/outbound/things");
        var plain = new AtomicReference<String>();
        var ambient = new AtomicReference<String>();
        var executor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "async-worker"));
        try {
            f.filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                plain.set(borrowOutcome(executor, f, request, false));
                ambient.set(borrowOutcome(executor, f, request, true));
            });
        } finally {
            executor.shutdownNow();
        }

        assertThat(plain.get()).as("an unbound thread never inherits the request's root")
            .isEqualTo("RECOVERY_OPERATION_REQUIRED");
        assertThat(ambient.get()).as("a carried request context is reported as lost, not granted")
            .isEqualTo("RECOVERY_OPERATION_CONTEXT_LOST");
    }

    @Test void destroyClearsABindingLeakedOnAnotherThread() throws Exception {
        var f = fixture();
        var request = new MockHttpServletRequest("GET", "/api/outbound/things");
        f.filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });
        var root = f.registry.live(token(request));
        var bound = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var afterDestroy = new AtomicReference<Object>();
        var executor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "leaked-dispatch"));
        try {
            var worker = executor.submit(() -> {
                root.bindDispatch(Thread.currentThread());
                bound.countDown();
                release.await(30, TimeUnit.SECONDS);
                afterDestroy.set(f.registry.bound(Thread.currentThread()));
                return null;
            });
            assertThat(bound.await(30, TimeUnit.SECONDS)).isTrue();
            f.destroy(request);
            assertThat(f.registry.liveRoots()).isZero();
            release.countDown();
            worker.get(30, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }

        assertThat(afterDestroy.get()).as("the destroy path is the last boundary, on every thread").isNull();
    }

    @Test void aRequestWhoseAuthorityMovedOnIsRefusedAndAdmitsNothing() throws Exception {
        var f = fixture();
        f.control.append(f.control.next("RECOVERING"));
        var request = new MockHttpServletRequest("GET", "/api/outbound/things");

        assertThatThrownBy(() -> f.filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { }))
            .hasMessage("CONTROL_INSTANCE_STALE");
        assertThat(token(request)).isNull();
        assertThat(f.registry.liveRoots()).isZero();
    }

    /**
     * The control lock refuses same-root nesting (design row 13), so a second admission attempt made
     * from inside the critical section is named as misuse instead of being queued: the entry admits
     * one root per critical section, and concurrent requests wait for it rather than being refused.
     */
    @Test void admissionIsRefusedWhenTheControlCriticalSectionIsAlreadyHeld() throws Exception {
        var f = fixture();
        var token = new RecoveryOperationToken(java.util.UUID.randomUUID(), RecoveryOperationKind.HTTP_REQUEST,
            f.registry.startup().installationId(), f.registry.startup().recordSha256(), null);

        assertThatThrownBy(() -> RecoveryControlLock.locked(f.control.root, () -> f.registry.admit(token)))
            .hasMessage("CONTROL_COORDINATOR_REENTRANT");
        assertThat(f.registry.liveRoots()).isZero();
    }

    /**
     * A multipart request is admitted like any other dispatch: the container stages the parts inside
     * this very chain (verified with the real container by the S4 field run), so exactly one root
     * covers the parse and the handler, and the root is retired with the request.
     */
    @Test void multipartRequestIsCoveredByTheSameSingleRootAndRetiredWithItsRequest() throws Exception {
        var f = fixture();
        var request = new org.springframework.mock.web.MockMultipartHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/outbound/import");
        request.addFile(new org.springframework.mock.web.MockMultipartFile("file", "probe.bin",
            "application/octet-stream", new byte[64]));
        var observed = new AtomicReference<Object>();

        f.filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            observed.set(f.registry.bound(Thread.currentThread()));
            assertThat(((org.springframework.web.multipart.MultipartHttpServletRequest) req).getFileMap())
                .as("the dispatch still sees the multipart request").isNotEmpty();
            borrows(f.adapter);
        });

        var token = token(request);
        assertThat(observed.get()).isEqualTo(token);
        assertThat(f.registry.liveRoots()).as("a multipart request creates one root, not one per part").isEqualTo(1);
        f.destroy(request);
        assertThat(f.registry.liveRoots()).isZero();
    }

    private static RecoveryOperationToken token(MockHttpServletRequest request) {
        return (RecoveryOperationToken) request.getAttribute(RecoveryOperationRegistry.REQUEST_ROOT_ATTRIBUTE);
    }

    /** A borrow inside a dispatch that must be admitted through the request's root. */
    private static void borrows(RecoveryRequestAdmittedDataSource adapter) throws java.io.IOException {
        try (Connection borrowed = adapter.getConnection()) {
            assertThat(borrowed).as("the request's root covers this database access").isNotNull();
        } catch (java.sql.SQLException error) {
            throw new java.io.IOException(error);
        }
    }

    private static void errorDispatch(MockHttpServletRequest request, RecoveryRequestAdmissionFilter filter, FilterChain chain)
            throws java.io.IOException {
        request.setDispatcherType(DispatcherType.ERROR);
        request.setAttribute(WebUtils.ERROR_REQUEST_URI_ATTRIBUTE, request.getRequestURI());
        try {
            filter.doFilter(request, new MockHttpServletResponse(), chain);
        } catch (jakarta.servlet.ServletException error) {
            throw new java.io.IOException(error);
        } finally {
            request.setDispatcherType(DispatcherType.REQUEST);
        }
    }

    private static String borrowOutcome(ExecutorService executor, Fixture fixture, MockHttpServletRequest request,
            boolean withRequestContext) throws java.io.IOException {
        try {
            return executor.submit(() -> {
                try {
                    if (withRequestContext) RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
                    try (Connection borrowed = fixture.adapter.getConnection()) { return "ADMITTED:" + borrowed; }
                } catch (RuntimeException | java.sql.SQLException error) {
                    return error.getMessage();
                } finally {
                    RequestContextHolder.resetRequestAttributes();
                }
            }).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new java.io.IOException(interrupted);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException error) {
            throw new java.io.IOException(error.getCause() == null ? error : error.getCause());
        }
    }

    private static Fixture fixture() throws Exception {
        var control = RecoveryWriterLeaseTest.protocol();
        var registry = new RecoveryOperationRegistry(control.location(), control.checkpoint);
        var source = mock(DataSource.class);
        var connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(source.getConnection()).thenReturn(connection);
        return new Fixture(control, registry, source, new RecoveryRequestAdmittedDataSource(source, registry),
            new RecoveryRequestAdmissionFilter(registry));
    }

    private record Fixture(RecoveryControlFixture control, RecoveryOperationRegistry registry, DataSource source,
            RecoveryRequestAdmittedDataSource adapter, RecoveryRequestAdmissionFilter filter) {
        void destroy(MockHttpServletRequest request) {
            new RecoveryRequestRootListener(registry)
                .requestDestroyed(new ServletRequestEvent(new MockServletContext(), request));
        }
    }
}
