package com.acrylic.outbound.recovery;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Design row 11 on the request-root path: every JDBC effect of a borrow passes the effect gate
 * before it reaches the driver, effect-free reads stay usable, and the default wiring attaches no
 * gate at all.
 */
class RecoveryRequestAdmittedDataSourceGateTest {
    private static final String UPDATE = "UPDATE recovery_entry_probe SET counter=counter+1";
    private static final String SELECT = "SELECT counter FROM recovery_entry_probe";

    @AfterEach void clearAmbientRequest() { RequestContextHolder.resetRequestAttributes(); }

    @Test void anAttachedGateIsConsultedBeforeEveryEffect() throws Exception {
        var f = fixture();
        var authority = new EffectTestAuthority();
        var connection = mock(Connection.class);
        var statement = mock(Statement.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.createStatement()).thenReturn(statement);
        when(f.source.getConnection()).thenReturn(connection);
        var gate = RecoveryEffectGate.enabled(authority);
        var adapter = new RecoveryRequestAdmittedDataSource(f.source, f.registry, gate);
        var root = f.register();
        try {
            root.bindDispatch(Thread.currentThread());
            try (Connection borrowed = adapter.getConnection()) {
                borrowed.commit();
                borrowed.createStatement().execute(UPDATE);
            }
            assertThat(authority.checks)
                .as("commit and the write statement are checked, each before delegation").isEqualTo(2);
            verify(connection).commit();
            verify(statement).execute(UPDATE);
        } finally {
            f.release(root);
        }
    }

    @Test void aRefusedEffectNeverReachesTheDriver() throws Exception {
        var f = fixture();
        var authority = new EffectTestAuthority();
        authority.denial = "RECOVERY_LEASE_FENCED";
        var connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(f.source.getConnection()).thenReturn(connection);
        var gate = RecoveryEffectGate.enabled(authority);
        var adapter = new RecoveryRequestAdmittedDataSource(f.source, f.registry, gate);
        var root = f.register();
        try {
            root.bindDispatch(Thread.currentThread());
            try (Connection borrowed = adapter.getConnection()) {
                assertThatThrownBy(borrowed::commit).hasMessageContaining("RECOVERY_LEASE_FENCED");
            }
            assertThat(authority.checks).as("the refusal is a gate decision").isEqualTo(1);
            verify(connection, never()).commit();
        } finally {
            f.release(root);
        }
    }

    /**
     * The read-only whitelist is deliberately narrow: a constant read is not an effect, while a read
     * from a relation is a {@code SOURCE_READ} and stays gated, because a view or stored function can
     * mutate. Unrecognised SQL is refused, never delegated on a guess.
     */
    @Test void aRelationReadIsGatedWhileAConstantReadIsNot() throws Exception {
        var f = fixture();
        var authority = new EffectTestAuthority();
        var connection = mock(Connection.class);
        var statement = mock(Statement.class);
        var prepared = mock(PreparedStatement.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.createStatement()).thenReturn(statement);
        when(connection.prepareStatement(UPDATE)).thenReturn(prepared);
        when(f.source.getConnection()).thenReturn(connection);
        var gate = RecoveryEffectGate.enabled(authority);
        var adapter = new RecoveryRequestAdmittedDataSource(f.source, f.registry, gate);
        var root = f.register();
        try {
            root.bindDispatch(Thread.currentThread());
            try (Connection borrowed = adapter.getConnection()) {
                borrowed.createStatement().executeQuery("SELECT 1");
                assertThat(authority.checks).as("a constant read is not an effect").isZero();
                verify(statement).executeQuery("SELECT 1");

                borrowed.createStatement().executeQuery(SELECT);
                assertThat(authority.checks).as("one check per relation read").isEqualTo(1);
                verify(statement).executeQuery(SELECT);

                authority.denial = "RECOVERY_LEASE_FENCED";
                assertThatThrownBy(() -> borrowed.createStatement().execute(SELECT))
                    .as("a relation read is refused while the gate denies").hasMessageContaining("RECOVERY_LEASE_FENCED");
                assertThatThrownBy(() -> borrowed.createStatement().execute(UPDATE))
                    .hasMessageContaining("RECOVERY_LEASE_FENCED");
                verify(statement, never()).execute(UPDATE);
                assertThatThrownBy(() -> borrowed.createStatement().execute("VACUUM something"))
                    .as("unrecognised SQL is refused instead of being delegated on a guess")
                    .hasMessageContaining("CONTROL_EFFECT_UNCLASSIFIED");
                // A prepared statement is classified by its own SQL when it executes.
                var preparedProxy = borrowed.prepareStatement(UPDATE);
                assertThatThrownBy(preparedProxy::executeUpdate).hasMessageContaining("RECOVERY_LEASE_FENCED");
                verify(prepared, never()).executeUpdate();
                // Constant reads keep working while every relation access is frozen.
                borrowed.createStatement().executeQuery("SELECT 1");
                verify(statement, org.mockito.Mockito.times(2)).executeQuery("SELECT 1");
            }
        } finally {
            f.release(root);
        }
    }

    @Test void withoutAnAttachedGateTheSameEffectsAreNotChecked() throws Exception {
        var f = fixture();
        var connection = mock(Connection.class);
        var statement = mock(Statement.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.createStatement()).thenReturn(statement);
        when(f.source.getConnection()).thenReturn(connection);
        var root = f.register();
        try {
            root.bindDispatch(Thread.currentThread());
            try (Connection borrowed = f.adapter.getConnection()) {
                borrowed.commit();
                borrowed.createStatement().execute(UPDATE);
            }
            verify(connection).commit();
            verify(statement).execute(UPDATE);
        } finally {
            f.release(root);
        }
    }

    @Test void aLocalFenceRefusesWithoutConsultingTheAuthority() throws Exception {
        var f = fixture();
        var authority = new EffectTestAuthority();
        var connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(f.source.getConnection()).thenReturn(connection);
        var gate = RecoveryEffectGate.enabled(authority);
        var adapter = new RecoveryRequestAdmittedDataSource(f.source, f.registry, gate);
        var root = f.register();
        try {
            root.bindDispatch(Thread.currentThread());
            gate.fence();
            try (Connection borrowed = adapter.getConnection()) {
                assertThatThrownBy(borrowed::commit).hasMessageContaining("RECOVERY_LEASE_FENCED");
            }
            assertThat(authority.checks).as("the local layer refuses before any authority call").isZero();
            verify(connection, never()).commit();
        } finally {
            f.release(root);
        }
    }

    private Fixture fixture() throws Exception {
        var control = RecoveryWriterLeaseTest.protocol();
        var registry = new RecoveryOperationRegistry(control.location(), control.checkpoint);
        var source = mock(DataSource.class);
        return new Fixture(control, registry, source,
            new RecoveryRequestAdmittedDataSource(source, registry, RecoveryEffectGate.disabled()));
    }

    private record Fixture(RecoveryControlFixture control, RecoveryOperationRegistry registry, DataSource source,
            RecoveryRequestAdmittedDataSource adapter) {
        RecoveryOperationLease register() throws Exception {
            var token = new RecoveryOperationToken(UUID.randomUUID(), RecoveryOperationKind.HTTP_REQUEST,
                control.installation, registry.startup().recordSha256(), null);
            return RecoveryControlLock.locked(control.root, () -> registry.register(token));
        }

        void release(RecoveryOperationLease root) {
            root.unbindAll();
            root.close();
        }
    }
}
