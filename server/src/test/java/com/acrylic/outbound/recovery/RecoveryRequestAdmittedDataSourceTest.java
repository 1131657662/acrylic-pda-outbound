package com.acrylic.outbound.recovery;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * S2: the primary data source resolves every borrow to the caller's operation root and refuses,
 * with one distinct code per condition, instead of ever falling back to the legacy path.
 */
class RecoveryRequestAdmittedDataSourceTest {
    @AfterEach void clearAmbientRequest() { RequestContextHolder.resetRequestAttributes(); }

    @Test void boundLiveRootBorrowsAndRegistersOneConnectionSubOperation() throws Exception {
        var f = fixture();
        var connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(f.source.getConnection()).thenReturn(connection);
        var root = f.register(RecoveryOperationKind.HTTP_REQUEST);
        try {
            root.bindDispatch(Thread.currentThread());

            try (Connection borrowed = f.adapter.getConnection()) {
                assertThat(borrowed).isNotNull();
                assertThat(root.openSubOperations()).as("the borrow is registered on its root").isEqualTo(1);
                assertThat(f.registry.bound(Thread.currentThread())).isEqualTo(root.token());
                assertThatThrownBy(() -> borrowed.unwrap(HikariDataSource.class))
                    .hasMessage("CONTROL_CONNECTION_UNWRAP_FORBIDDEN");
            }

            assertThat(root.openSubOperations()).as("connection close released its sub-operation").isZero();
            assertThat(root.phase()).isEqualTo(RecoveryOperationPhase.SERVING);
        } finally {
            f.release(root);
        }
        assertThat(f.adapter.unwrap(DataSource.class)).isSameAs(f.adapter);
        assertThatThrownBy(() -> f.adapter.unwrap(HikariDataSource.class))
            .hasMessage("CONTROL_DATASOURCE_UNWRAP_FORBIDDEN");
        verify(f.source).getConnection();
    }

    @Test void eachBorrowTakesAndReleasesItsOwnWriterReference() throws Exception {
        var f = fixture();
        var connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(f.source.getConnection()).thenReturn(connection);
        var root = f.register(RecoveryOperationKind.HTTP_REQUEST);
        try {
            root.bindDispatch(Thread.currentThread());
            // The root keeps its own reference across both connections; a close that dropped it would
            // make the second borrow fail instead of handing out a fresh reference.
            try (Connection first = f.adapter.getConnection()) { assertThat(first).isNotNull(); }
            try (Connection second = f.adapter.getConnection()) { assertThat(second).isNotNull(); }
            assertThat(root.phase()).isEqualTo(RecoveryOperationPhase.SERVING);
            assertThat(root.openSubOperations()).isZero();
        } finally {
            f.release(root);
        }
        verify(f.source, times(2)).getConnection();
        verify(connection, times(2)).close();
    }

    @Test void requestRootWithoutAThreadBindingIsRefusedAsLostContext() throws Exception {
        var f = fixture();
        var root = f.register(RecoveryOperationKind.HTTP_REQUEST);
        try {
            publishAmbientRoot(root.token());

            assertThatThrownBy(f.adapter::getConnection).hasMessage("RECOVERY_OPERATION_CONTEXT_LOST");
            verifyNoInteractions(f.source);
        } finally {
            f.release(root);
        }
    }

    /** A borrow that never reached a connection must not leave the root's retained work behind. */
    @Test void failedBorrowReleasesTheRootsRetainedWorkAndKeepsItUsable() throws Exception {
        var f = fixture();
        var connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(f.source.getConnection()).thenThrow(new SQLException("borrow failed")).thenReturn(connection);
        var root = f.register(RecoveryOperationKind.HTTP_REQUEST);
        try {
            root.bindDispatch(Thread.currentThread());

            assertThatThrownBy(f.adapter::getConnection).hasMessage("borrow failed");
            assertThat(root.openSubOperations()).as("no sub-operation survives a failed borrow").isZero();
            assertThat(root.phase()).isEqualTo(RecoveryOperationPhase.SERVING);

            try (Connection borrowed = f.adapter.getConnection()) {
                assertThat(borrowed).as("the failed borrow released only its own reference").isNotNull();
            }
            assertThat(root.openSubOperations()).isZero();
        } finally {
            f.release(root);
        }
    }

    @Test void threadWithoutAnyRootMarkerIsRefusedInsteadOfFallingBack() throws Exception {
        var f = fixture();
        var root = f.register(RecoveryOperationKind.HTTP_REQUEST);
        try {
            assertThatThrownBy(f.adapter::getConnection).hasMessage("RECOVERY_OPERATION_REQUIRED");
            // A request that never carried a root marker is still "no root identifier", not "lost context".
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
            assertThatThrownBy(f.adapter::getConnection).hasMessage("RECOVERY_OPERATION_REQUIRED");
            verifyNoInteractions(f.source);
        } finally {
            f.release(root);
        }
    }

    @Test void borrowThroughAReleasedRootIsRefusedWithTheReleasedCode() throws Exception {
        var f = fixture();
        var connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(f.source.getConnection()).thenReturn(connection);
        var root = f.register(RecoveryOperationKind.HTTP_REQUEST);
        root.bindDispatch(Thread.currentThread());
        try (Connection first = f.adapter.getConnection()) { assertThat(first).isNotNull(); }

        // The dispatch owner has not cleaned up yet, so the stale binding is what the borrow finds:
        // the token no longer resolves, and that is released rather than "no root at all".
        root.close();
        assertThat(f.registry.liveRoots()).isZero();
        assertThat(f.registry.bound(Thread.currentThread())).as("the binding outlives the retired root")
            .isEqualTo(root.token());
        assertThatThrownBy(f.adapter::getConnection).hasMessage("CONTROL_OPERATION_RELEASED");

        root.unbindAll();
        assertThat(f.registry.bound(Thread.currentThread())).isNull();
        assertThatThrownBy(f.adapter::getConnection).hasMessage("RECOVERY_OPERATION_REQUIRED");
        verify(f.source, times(1)).getConnection();
    }

    @Test void subOperationOutsideTheRootSetIsRefused() throws Exception {
        var f = fixture();
        // A rendering hand-off may read evidence and hand work off, never borrow a business connection.
        var root = f.register(RecoveryOperationKind.RENDER_PROCESS);
        try {
            root.bindDispatch(Thread.currentThread());

            assertThatThrownBy(f.adapter::getConnection).hasMessage("CONTROL_SUBOPERATION_FORBIDDEN");
            verifyNoInteractions(f.source);
            assertThat(root.openSubOperations()).isZero();
        } finally {
            f.release(root);
        }
    }

    @Test void rootThatStartedReleasingRefusesNewSubOperations() throws Exception {
        var f = fixture();
        var root = f.register(RecoveryOperationKind.HTTP_REQUEST);
        try {
            root.bindDispatch(Thread.currentThread());
            root.enterReleasing();

            assertThatThrownBy(f.adapter::getConnection).hasMessage("CONTROL_SUBOPERATION_FORBIDDEN");
            verifyNoInteractions(f.source);
        } finally {
            f.release(root);
        }
    }

    /** A root in its finalizing phase still covers the retained write that runs after the chain. */
    @Test void finalizingRootStillAcceptsRetainedWork() throws Exception {
        var f = fixture();
        var connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(f.source.getConnection()).thenReturn(connection);
        var root = f.register(RecoveryOperationKind.HTTP_REQUEST);
        try {
            root.bindDispatch(Thread.currentThread());
            root.enterFinalizing();

            try (Connection borrowed = f.adapter.getConnection()) { assertThat(borrowed).isNotNull(); }
            assertThat(root.phase()).isEqualTo(RecoveryOperationPhase.FINALIZING);
            assertThat(root.openSubOperations()).isZero();
        } finally {
            f.release(root);
        }
        verify(f.source).getConnection();
    }

    @Test void fencedRootIsRefusedWithTheFenceCode() throws Exception {
        var f = fixture();
        var root = f.register(RecoveryOperationKind.HTTP_REQUEST);
        try {
            root.bindDispatch(Thread.currentThread());
            root.fence();

            assertThatThrownBy(f.adapter::getConnection).hasMessage("RECOVERY_LEASE_FENCED");
            verifyNoInteractions(f.source);
        } finally {
            f.release(root);
        }
    }

    /** The depth rule of the dispatch binding: a nested dispatch keeps the outer scope alive. */
    @Test void nestedDispatchBindingKeepsTheOuterScopeUntilItEnds() throws Exception {
        var f = fixture();
        var root = f.register(RecoveryOperationKind.HTTP_REQUEST);
        try {
            root.bindDispatch(Thread.currentThread());
            root.bindDispatch(Thread.currentThread());
            assertThat(root.boundDepth(Thread.currentThread())).isEqualTo(2);

            root.unbindDispatch(Thread.currentThread());
            assertThat(root.boundDepth(Thread.currentThread())).isEqualTo(1);
            assertThat(f.registry.bound(Thread.currentThread())).isEqualTo(root.token());

            root.unbindDispatch(Thread.currentThread());
            assertThat(root.boundDepth(Thread.currentThread())).isZero();
            assertThat(f.registry.bound(Thread.currentThread())).isNull();
        } finally {
            f.release(root);
        }
    }

    private static void publishAmbientRoot(RecoveryOperationToken token) {
        var request = new MockHttpServletRequest();
        request.setAttribute(RecoveryOperationRegistry.REQUEST_ROOT_ATTRIBUTE, token);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static Fixture fixture() throws Exception {
        var control = RecoveryWriterLeaseTest.protocol();
        var registry = new RecoveryOperationRegistry(control.location(), control.checkpoint);
        var source = mock(DataSource.class);
        return new Fixture(control, registry, source, new RecoveryRequestAdmittedDataSource(source, registry));
    }

    private record Fixture(RecoveryControlFixture control, RecoveryOperationRegistry registry, DataSource source,
            RecoveryRequestAdmittedDataSource adapter) {
        RecoveryOperationLease register(RecoveryOperationKind kind) throws Exception {
            var token = new RecoveryOperationToken(UUID.randomUUID(), kind, control.installation,
                registry.startup().recordSha256(), null);
            return RecoveryControlLock.locked(control.root, () -> registry.register(token));
        }

        /** The real lifecycle: the dispatch owner clears its bindings, then the root is retired. */
        void release(RecoveryOperationLease root) {
            root.unbindAll();
            root.close();
        }
    }
}
