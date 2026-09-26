package com.acrylic.outbound.common;

import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServerStateTest {
    private final DataSource source = mock(DataSource.class);
    private final ServerState state = new ServerState(new JdbcTemplate(source));

    @AfterEach void clear() {
        TransactionSynchronizationManager.clear();
        TransactionSynchronizationManager.unbindResourceIfPossible(source);
    }

    @Test void autocommitCannotPretendToHoldARecoveryFence() {
        assertThatThrownBy(() -> state.lockWritable("1")).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(source);
    }

    @Test void anotherTransactionWithoutThisDatasourceCannotHoldTheFence() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThatThrownBy(state::lockCurrentWritable).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(source);
    }

    @Test void boundResourceWithoutActiveTransactionCannotHoldTheFence() {
        TransactionSynchronizationManager.bindResource(source, new Object());
        assertThatThrownBy(state::lockCurrentWritable).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(source);
    }
}
