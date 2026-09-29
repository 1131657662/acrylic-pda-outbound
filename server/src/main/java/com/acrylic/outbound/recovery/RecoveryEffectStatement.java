package com.acrylic.outbound.recovery;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Remembers SQL at prepare/addBatch time; every execution reclassifies the retained text. */
final class RecoveryEffectStatement {
    private final String preparedSql;
    private final List<String> batch = new ArrayList<>();
    private boolean batchKnown;
    private boolean batchUncertain;

    RecoveryEffectStatement(String preparedSql) { this.preparedSql = preparedSql; }

    List<RecoveryEffectSql.Kind> classify(String name, Object[] args) throws SQLException {
        if (name.equals("executeBatch") || name.equals("executeLargeBatch")) return classifyBatch();
        if (name.startsWith("execute") || name.equals("addBatch"))
            return List.of(RecoveryEffectSql.classify(sql(args)));
        return List.of();
    }

    void succeeded(String name, Object[] args) {
        if (name.equals("addBatch")) { batch.add(sql(args)); batchKnown = true; }
        if (name.equals("clearBatch") || name.equals("executeBatch") || name.equals("executeLargeBatch")) {
            batch.clear(); batchKnown = true; batchUncertain = false;
        }
    }

    void failed(String name) {
        if (name.contains("Batch")) batchUncertain = true;
    }

    private List<RecoveryEffectSql.Kind> classifyBatch() throws SQLException {
        if (batchUncertain) throw RecoveryEffectGate.unclassified();
        if (!batchKnown && preparedSql == null) throw RecoveryEffectGate.unclassified();
        // A prepared statement without any queued parameters is still bound to known SQL.
        if (!batchKnown) return List.of(RecoveryEffectSql.classify(preparedSql));
        List<RecoveryEffectSql.Kind> kinds = new ArrayList<>();
        for (String sql : batch) kinds.add(RecoveryEffectSql.classify(sql));
        return List.copyOf(kinds);
    }

    String sql(Object[] args) {
        if (args != null && args.length > 0) return args[0] instanceof String text ? text : null;
        return preparedSql;
    }
}
