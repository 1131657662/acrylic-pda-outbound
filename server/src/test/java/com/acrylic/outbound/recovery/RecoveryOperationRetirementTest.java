package com.acrylic.outbound.recovery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Root protocol behavior; channel failures here are explicitly MODEL faults. */
class RecoveryOperationRetirementTest {
    @Test void normalCloseRetiresExactlyOnce() throws Exception {
        var f = fixture(); var root = f.root();
        root.close(); root.close();
        assertThat(f.channel.retired).isEqualTo(1);
        assertThat(f.registry.liveRoots()).isZero();
        assertThat(root.phase()).isEqualTo(RecoveryOperationPhase.RELEASED);
    }

    @Test void openChildKeepsTheRootAndInvokesUnknown() throws Exception {
        var f = fixture(); var root = f.root();
        var child = root.registerSubOperation(RecoverySubOperation.CONNECTION);
        assertThatThrownBy(root::close).hasMessage("CONTROL_OPERATION_RETIREMENT_UNCONFIRMED");
        child.close(); root.close();
        assertQuarantined(f, root);
    }

    @Test void publishedRootCannotRetireWhileStillBound() throws Exception {
        var f = fixture(); var root = f.root(); root.bindDispatch(Thread.currentThread());
        assertThatThrownBy(root::close).hasRootCauseMessage("CONTROL_OPERATION_BINDINGS_ACTIVE");
        root.unbindAll(); root.close();
        assertQuarantined(f, root);
    }

    @Test void closedPoisonedConnectionIsNotAConfirmedTerminalOutcome() throws Exception {
        var f = fixture(); var root = f.root(); root.bindDispatch(Thread.currentThread());
        var source = mock(DataSource.class); var raw = mock(Connection.class);
        when(raw.getAutoCommit()).thenReturn(true); when(source.getConnection()).thenReturn(raw);
        doThrow(new SQLException("MODEL lost commit receipt")).when(raw).commit();
        try (var connection = new RecoveryRequestAdmittedDataSource(source, f.registry).getConnection()) {
            assertThatThrownBy(connection::commit).hasMessage("MODEL lost commit receipt");
        }
        root.unbindAll();
        assertThat(root.openSubOperations()).isZero();
        assertThatThrownBy(root::close).hasRootCauseMessage("CONTROL_OPERATION_OUTCOME_UNCONFIRMED");
        assertQuarantined(f, root);
    }

    @Test void failedDecisionCannotReleaseTheRoot() throws Exception {
        var f = fixture(); f.channel.fail = true; var root = f.root();
        assertThatThrownBy(root::close).hasMessage("CONTROL_OPERATION_RETIREMENT_UNCONFIRMED");
        assertQuarantined(f, root);
    }

    @Test void cleanupFailureAfterConfirmedRetirementDoesNotPublishUnknown() throws Exception {
        var f = fixture(); f.channel.clean = false; var root = f.root(); root.close();
        assertThat(f.channel.retired).isEqualTo(1);
        assertThat(f.channel.unknown).isZero();
        assertThat(f.registry.liveRoots()).isZero();
        assertThat(root.phase()).isEqualTo(RecoveryOperationPhase.FENCED);
        assertThatThrownBy(f::root).hasMessage("CONTROL_WRITERS_ACTIVE");
    }

    private static void assertQuarantined(Fixture f, RecoveryOperationLease root) {
        assertThat(f.channel.retired).isZero(); assertThat(f.channel.unknown).isEqualTo(1);
        assertThat(f.registry.liveRoots()).isEqualTo(1);
        assertThat(root.phase()).isEqualTo(RecoveryOperationPhase.FENCED);
        assertThatThrownBy(f::root).hasMessage("CONTROL_WRITERS_ACTIVE");
    }

    private static Fixture fixture() throws Exception {
        var control = RecoveryWriterLeaseTest.protocol(); var channel = new Channel();
        return new Fixture(new RecoveryOperationRegistry(control.location(), control.checkpoint, channel), channel);
    }

    private record Fixture(RecoveryOperationRegistry registry, Channel channel) {
        RecoveryOperationLease root() {
            return registry.admit(new RecoveryOperationToken(UUID.randomUUID(), RecoveryOperationKind.HTTP_REQUEST,
                registry.startup().installationId(), registry.startup().recordSha256(), null));
        }
    }

    private static final class Channel implements RecoveryOperationRegistry.LeaseChannel {
        int retired, unknown; boolean fail, clean = true;
        public UUID publish() { return UUID.randomUUID(); }
        public boolean retire(UUID id) throws SQLException {
            if (fail) throw new SQLException("MODEL disk failure");
            retired++; return clean;
        }
        public void unknown() { unknown++; }
    }
}
