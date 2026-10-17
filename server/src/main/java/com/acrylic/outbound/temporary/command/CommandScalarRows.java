package com.acrylic.outbound.temporary.command;

import java.sql.*;
import org.springframework.jdbc.core.ColumnMapRowMapper;

/** Historical command rows contain scalar values only; never obtain opaque JDBC locators. */
final class CommandScalarRows extends ColumnMapRowMapper {
    static final CommandScalarRows ROWS = new CommandScalarRows();

    private CommandScalarRows() { }

    @Override protected Object getColumnValue(ResultSet result, int column) throws SQLException {
        Object value = switch (result.getMetaData().getColumnType(column)) {
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> result.getBytes(column);
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR,
                 Types.LONGNVARCHAR -> result.getString(column);
            case Types.BOOLEAN, Types.BIT -> result.getBoolean(column);
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER -> result.getInt(column);
            case Types.BIGINT, Types.DECIMAL, Types.NUMERIC -> result.getBigDecimal(column);
            case Types.REAL, Types.FLOAT, Types.DOUBLE -> result.getDouble(column);
            case Types.DATE -> result.getDate(column);
            case Types.TIME -> result.getTime(column);
            case Types.TIMESTAMP -> result.getTimestamp(column);
            case Types.NULL -> null;
            default -> throw new SQLException("COMMAND_SCALAR_TYPE_UNSUPPORTED");
        };
        return result.wasNull() ? null : value;
    }
}
