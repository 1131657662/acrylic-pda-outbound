package com.acrylic.outbound.recovery;

import java.sql.*;
import javax.sql.DataSource;
import static org.mockito.Mockito.*;

final class EffectUnitFixture implements AutoCloseable {
    final Connection physical = mock(Connection.class);
    final EffectTestAuthority authority = new EffectTestAuthority();
    final RecoveryEffectGate gate = RecoveryEffectGate.enabled(authority);
    final Connection connection;
    final RecoveryAdmittedDataSource adapter;

    EffectUnitFixture(boolean enabled) throws Exception {
        var control = RecoveryWriterLeaseTest.protocol();
        var source = mock(DataSource.class);
        when(source.getConnection()).thenReturn(physical);
        when(physical.getAutoCommit()).thenReturn(true);
        var original = new RecoveryAdmittedDataSource(source, control.location(), control.checkpoint);
        adapter = enabled ? new RecoveryAdmittedDataSource(original, gate) : original;
        connection = adapter.getConnection();
    }

    void deny() { authority.denial = "RECOVERY_LEASE_OUTSTANDING"; }
    @Override public void close() throws SQLException { connection.close(); }
}
