package com.acrylic.outbound.temporary.evidence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SourceEvidenceIntakeTransactionTest {
    @Test void independentRecheckSuspendsOuterTransactionAndUsesReadCommittedConnection() throws Exception {
        var dataSource=mock(DataSource.class);
        var outerConnection=mock(Connection.class);
        var recheckConnection=mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(outerConnection,recheckConnection);
        when(outerConnection.getAutoCommit()).thenReturn(true);
        when(recheckConnection.getAutoCommit()).thenReturn(true);
        when(recheckConnection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_REPEATABLE_READ);
        var manager=new DataSourceTransactionManager(dataSource);
        var jdbc=mock(JdbcTemplate.class);
        var factory=new ProxyFactory(new SourceEvidenceIntakeReceipts(jdbc));
        factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        var receipts=(SourceEvidenceIntakeReceipts)factory.getProxy();
        var key=new SourceEvidenceIntakeReceipts.OriginalKey(UUID.randomUUID(),UUID.randomUUID());
        byte[] request="{\"operation\":\"OPEN\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(invocation->{
            assertThat(DataSourceUtils.getConnection(dataSource)).isSameAs(recheckConnection);
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                .isEqualTo(TransactionDefinition.ISOLATION_READ_COMMITTED);
            return List.of();
        });

        var result=new TransactionTemplate(manager).execute(status->{
            assertThat(DataSourceUtils.getConnection(dataSource)).isSameAs(outerConnection);
            var decision=receipts.recheckAfterRollback(key,request);
            assertThat(DataSourceUtils.getConnection(dataSource)).isSameAs(outerConnection);
            return decision;
        });

        assertThat(result.status()).isEqualTo(SourceEvidenceIntakeReplay.Status.NOT_FOUND);
        verify(recheckConnection).setReadOnly(true);
        verify(recheckConnection).setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        verify(recheckConnection).commit();
        verify(outerConnection).commit();
    }

    @Test void duplicateWriteRollsBackBeforeIndependentRead() throws Exception {
        var dataSource=mock(DataSource.class);
        var writeConnection=mock(Connection.class);
        var readConnection=mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(writeConnection,readConnection);
        when(writeConnection.getAutoCommit()).thenReturn(true);
        when(readConnection.getAutoCommit()).thenReturn(true);
        when(readConnection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_REPEATABLE_READ);
        var manager=new DataSourceTransactionManager(dataSource);
        var jdbc=mock(JdbcTemplate.class);
        var factory=new ProxyFactory(new SourceEvidenceIntakeReceipts(jdbc));
        factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        var receipts=(SourceEvidenceIntakeReceipts)factory.getProxy();
        var key=new SourceEvidenceIntakeReceipts.OriginalKey(UUID.randomUUID(),UUID.randomUUID());
        byte[] request="{\"operation\":\"OPEN\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var collision=new DuplicateKeyException("insert failed",new SQLException(
            "Duplicate entry 'value' for key 'uq_evidence_intake_original'","23000",1062));
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(invocation->{
            if(!TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                assertThat(DataSourceUtils.getConnection(dataSource)).isSameAs(writeConnection);
                throw collision;
            }
            verify(writeConnection).rollback();
            assertThat(DataSourceUtils.getConnection(dataSource)).isSameAs(readConnection);
            return List.of();
        });

        assertThatThrownBy(()->new TransactionTemplate(manager).execute(
            status->receipts.existing(key.principalId(),key.originalKey(),request))).isSameAs(collision);
        var decision=new SourceEvidenceIntakeRecovery(receipts).resolveAfterRollback(collision,key,request);

        assertThat(decision.status()).isEqualTo(SourceEvidenceIntakeReplay.Status.UNKNOWN);
        verify(readConnection).setReadOnly(true);
        verify(readConnection).setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        verify(readConnection).commit();
    }
}
