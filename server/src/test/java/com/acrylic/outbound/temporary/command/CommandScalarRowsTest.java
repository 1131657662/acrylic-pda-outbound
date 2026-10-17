package com.acrylic.outbound.temporary.command;

import java.math.BigDecimal;
import java.sql.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommandScalarRowsTest {
    @Test void historicalBinaryTextUnsignedNumberAndTimeKeepTheirValues() throws Exception {
        var result = mock(ResultSet.class); var metadata = mock(ResultSetMetaData.class);
        when(result.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(5);
        String[] names = {"id", "signed_receipt", "source_recovery_epoch", "recorded_at", "nullable"};
        int[] types = {Types.BINARY, Types.LONGVARCHAR, Types.BIGINT, Types.TIMESTAMP, Types.VARCHAR};
        for (int i = 0; i < names.length; i++) {
            when(metadata.getColumnLabel(i + 1)).thenReturn(names[i]);
            when(metadata.getColumnType(i + 1)).thenReturn(types[i]);
        }
        byte[] id = new byte[16]; id[15] = 7;
        var time = Timestamp.valueOf("2026-10-17 11:00:00.123");
        when(result.getBytes(1)).thenReturn(id);
        when(result.getString(2)).thenReturn("原凭据");
        when(result.getBigDecimal(3)).thenReturn(new BigDecimal("18446744073709551615"));
        when(result.getTimestamp(4)).thenReturn(time);
        var row = new CommandRecord(CommandScalarRows.ROWS.mapRow(result, 0));
        assertThat(row.id("id").toString()).isEqualTo("00000000-0000-0000-0000-000000000007");
        assertThat(row.text("signed_receipt")).isEqualTo("原凭据");
        assertThat(row.text("source_recovery_epoch")).isEqualTo("18446744073709551615");
        assertThat(row.recordedAt()).isEqualTo(time.toInstant());
        assertThat(row.text("nullable")).isNull();
        verify(result, never()).getObject(anyInt());
    }

    @Test void sqlNullDoesNotTurnIntoFalseOrZero() throws Exception {
        var result = mock(ResultSet.class); var metadata = mock(ResultSetMetaData.class);
        when(result.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("value");
        when(metadata.getColumnType(1)).thenReturn(Types.BOOLEAN);
        when(result.wasNull()).thenReturn(true);
        assertThat(CommandScalarRows.ROWS.mapRow(result, 0)).containsEntry("value", null);
    }

    @ParameterizedTest @ValueSource(ints = {Types.BLOB, Types.CLOB, Types.ARRAY, Types.REF, Types.SQLXML, Types.OTHER})
    void opaqueValuesAreRejectedWithoutObtainingALocator(int type) throws Exception {
        var result = mock(ResultSet.class); var metadata = mock(ResultSetMetaData.class);
        when(result.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("opaque");
        when(metadata.getColumnType(1)).thenReturn(type);
        assertThatThrownBy(() -> CommandScalarRows.ROWS.mapRow(result, 0)).hasMessage("COMMAND_SCALAR_TYPE_UNSUPPORTED");
        verify(result, never()).getObject(anyInt());
        verify(result, never()).getBlob(anyInt());
        verify(result, never()).getClob(anyInt());
    }
}
