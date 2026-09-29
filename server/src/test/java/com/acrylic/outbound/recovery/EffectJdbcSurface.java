package com.acrylic.outbound.recovery;

import java.lang.reflect.Method;
import java.sql.*;
import java.util.*;

/** Independent standard-API inventory. New/unassigned names fail the audit rather than silently pass. */
final class EffectJdbcSurface {
    private static final Set<String> OPAQUE = words("getObject getBlob getClob getNClob getSQLXML getArray getRef");
    private static final Set<String> VALUES = words("getAsciiStream getBigDecimal getBinaryStream getBoolean getByte "
        + "getBytes getCharacterStream getDate getDouble getFloat getInt getLong getNCharacterStream getNString "
        + "getRowId getShort getString getTime getTimestamp getUnicodeStream getURL getWarnings");
    private static final Set<String> CURSOR_LOCAL = words("next previous first last absolute relative beforeFirst afterLast "
        + "wasNull findColumn rowUpdated rowInserted rowDeleted clearWarnings setFetchSize setFetchDirection "
        + "cancelRowUpdates moveToCurrentRow getConcurrency getCursorName getFetchDirection getFetchSize getHoldability "
        + "getMetaData getRow getType isAfterLast isBeforeFirst isClosed isFirst isLast");
    private static final Set<String> STATEMENT_LOCAL = words("clearBatch clearParameters clearWarnings cancel "
        + "closeOnCompletion registerOutParameter getFetchDirection getFetchSize getLargeMaxRows getLargeUpdateCount "
        + "getMaxFieldSize getMaxRows getMoreResults getQueryTimeout getResultSetConcurrency getResultSetHoldability "
        + "getResultSetType getUpdateCount isClosed isCloseOnCompletion isPoolable isSimpleIdentifier "
        + "getMetaData getParameterMetaData");
    private static final Set<String> SETTERS = words("setCursorName setEscapeProcessing setFetchDirection setFetchSize "
        + "setLargeMaxRows setMaxFieldSize setMaxRows setPoolable setQueryTimeout setArray setAsciiStream setBigDecimal "
        + "setBinaryStream setBlob setBoolean setByte setBytes setCharacterStream setClob setDate setDouble setFloat "
        + "setInt setLong setNCharacterStream setNClob setNString setNull setObject setRef setRowId setShort setSQLXML "
        + "setString setTime setTimestamp setUnicodeStream setURL");
    private static final Set<String> UPDATES = words("updateArray updateAsciiStream updateBigDecimal updateBinaryStream "
        + "updateBlob updateBoolean updateByte updateBytes updateCharacterStream updateClob updateDate updateDouble "
        + "updateFloat updateInt updateLong updateNCharacterStream updateNClob updateNString updateNull updateObject "
        + "updateRef updateRowId updateShort updateSQLXML updateString updateTime updateTimestamp "
        + "insertRow updateRow deleteRow moveToInsertRow");

    private EffectJdbcSurface() { }
    private static Set<String> words(String text) { return Set.of(text.split(" ")); }

    static String decision(Class<?> api, Method method) {
        String name = method.getName();
        if (Set.of("unwrap", "isWrapperFor").contains(name)) return "PROXY_ONLY";
        if (name.equals("close")) return "CLEANUP";
        if (OPAQUE.contains(name)) return "DENY_OPAQUE";
        if (api != ResultSet.class && name.equals("getMetaData")) return "DENY_METADATA_EXECUTION";
        if (Set.of("getConnection", "getStatement", "getResultSet", "getGeneratedKeys").contains(name)) return "BOUND_HANDLE";
        if (api == ResultSet.class) {
            if (UPDATES.contains(name)) return "CHECK_EFFECT";
            if (VALUES.contains(name) || CURSOR_LOCAL.contains(name)) return "READ_LOCAL";
            if (name.equals("refreshRow")) return "DENY_UNKNOWN";
        } else {
            if (Set.of("execute", "executeQuery", "executeUpdate", "executeLargeUpdate", "executeBatch",
                    "executeLargeBatch", "addBatch").contains(name)) return "SQL_OR_BATCH";
            if (VALUES.contains(name) || STATEMENT_LOCAL.contains(name) || SETTERS.contains(name)) return "LOCAL_OR_VALUE";
            if (Set.of("wasNull", "enquoteIdentifier", "enquoteLiteral", "enquoteNCharLiteral").contains(name)) return "DENY_UNKNOWN";
        }
        throw new AssertionError("UNASSIGNED_API: " + method);
    }
}
