package com.acrylic.outbound.recovery;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Untrusted JDBC result boundaries fail rather than returning a partial inventory. */
class RecoveryReleaseInventoryLimitTest {
    @Test void rejectsTableBeyondRowBoundWithoutReturningTruncatedFacts() throws Exception {
        var jdbc=rows(65537,new byte[]{1});
        assertThatThrownBy(()->new RecoveryReleaseInventoryRows(jdbc,UUID.randomUUID()).read())
            .hasMessage("RECOVERY_RELEASE_INVENTORY_LIMIT");
    }

    @Test void rejectsAggregateBytesBeyondBoundAcrossRows() throws Exception {
        var jdbc=rows(2,new byte[8388609]);
        assertThatThrownBy(()->new RecoveryReleaseInventoryRows(jdbc,UUID.randomUUID()).read())
            .hasMessage("RECOVERY_RELEASE_INVENTORY_BYTES");
    }

    @SuppressWarnings("unchecked")
    private static JdbcTemplate rows(int count,byte[] value) throws Exception {
        var jdbc=mock(JdbcTemplate.class); var result=mock(ResultSet.class); var metadata=mock(ResultSetMetaData.class);
        when(result.getMetaData()).thenReturn(metadata); when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("id"); when(result.getBytes(1)).thenReturn(value);
        var index=new AtomicInteger(); when(result.next()).thenAnswer(call->index.getAndIncrement()<count);
        doAnswer(call->((ResultSetExtractor<Object>)call.getArgument(1)).extractData(result))
            .when(jdbc).query(anyString(),any(ResultSetExtractor.class),any(Object[].class));
        return jdbc;
    }
}
