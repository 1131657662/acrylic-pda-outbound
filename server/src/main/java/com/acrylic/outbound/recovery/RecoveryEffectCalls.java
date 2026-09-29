package com.acrylic.outbound.recovery;

import java.sql.SQLException;
import java.util.Set;

/** Explicit JDBC method surface, including cursor staging; unknown calls cannot reach a driver. */
final class RecoveryEffectCalls {
    private static final Set<String> CURSOR_READ = Set.of("next", "previous", "first", "last", "absolute",
        "relative", "beforeFirst", "afterLast", "wasNull", "findColumn", "rowUpdated", "rowInserted", "rowDeleted",
        "close", "clearWarnings", "setFetchSize", "setFetchDirection", "cancelRowUpdates", "moveToCurrentRow");
    private static final Set<String> CURSOR_WRITE = Set.of("insertRow", "updateRow", "deleteRow", "moveToInsertRow");
    private static final Set<String> UNSAFE_VALUES = Set.of("getBlob", "getClob", "getNClob", "getSQLXML",
        "getArray", "getRef", "getObject");
    private static final Set<String> STATEMENT_LOCAL = Set.of("close", "clearBatch", "clearParameters", "clearWarnings",
        "cancel", "closeOnCompletion", "registerOutParameter");

    private RecoveryEffectCalls() { }

    static boolean cursorEffect(String name) throws SQLException {
        if (CURSOR_WRITE.contains(name) || name.startsWith("update")) return true;
        if (UNSAFE_VALUES.contains(name)) throw RecoveryEffectGate.unclassified();
        if (CURSOR_READ.contains(name) || name.startsWith("get") || name.startsWith("is")) return false;
        throw RecoveryEffectGate.unclassified();
    }

    static void requireStatementMethod(String name) throws SQLException {
        // Callable OUT getters have the same opaque/mutable escape surface as ResultSet.
        if (UNSAFE_VALUES.contains(name)) throw RecoveryEffectGate.unclassified();
        // Client prepared metadata may execute the original SQL, including a mutating function.
        if (name.equals("getMetaData")) throw RecoveryEffectGate.unclassified();
        if (name.startsWith("execute") || name.equals("addBatch") || STATEMENT_LOCAL.contains(name)) return;
        if (name.startsWith("get") || name.startsWith("set") || name.startsWith("is")) return;
        throw RecoveryEffectGate.unclassified();
    }
}
