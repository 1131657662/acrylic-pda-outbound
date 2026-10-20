package com.acrylic.outbound.recovery;

import java.sql.SQLException;
import java.util.Set;

/** Finite scalar/predicate grammar; stored functions, qualified calls and unknown tokens are refused. */
final class RecoverySqlExpression {
    private static final Set<String> FUNCTIONS = Set.of("BIN_TO_UUID", "UUID_TO_BIN", "UTC_TIMESTAMP",
        "COUNT", "MAX", "MIN", "SUM", "COALESCE", "HEX", "JSON_OBJECT", "DATE_FORMAT");
    private static final Set<String> COMPARISONS = Set.of("=", "<>", "!=", "<=", ">=", "<", ">");
    private static final Set<String> TYPES = Set.of("CHAR", "BINARY", "JSON", "SIGNED", "UNSIGNED", "DECIMAL", "DATETIME");
    private static final Set<String> UNITS = Set.of("DAY", "HOUR", "MINUTE", "SECOND", "MICROSECOND");
    private final RecoverySqlTokens tokens;

    RecoverySqlExpression(RecoverySqlTokens tokens) { this.tokens = tokens; }

    void read() throws SQLException {
        tokens.enter();
        try { and(); while (tokens.take("OR")) and(); }
        finally { tokens.leave(); }
    }

    private void and() throws SQLException { predicate(); while (tokens.take("AND")) predicate(); }

    private void predicate() throws SQLException {
        while (tokens.take("NOT")) { /* prefix negation */ }
        if (tokens.take("EXISTS")) { tokens.need("("); RecoverySelectSql.query(tokens); tokens.need(")"); return; }
        sum();
        if (COMPARISONS.contains(tokens.peek())) { tokens.need(tokens.peek()); sum(); return; }
        if (tokens.take("IS")) { tokens.take("NOT"); tokens.need("NULL"); return; }
        boolean negated = tokens.take("NOT");
        if (tokens.take("IN")) { in(); return; }
        if (tokens.take("BETWEEN")) { sum(); tokens.need("AND"); sum(); return; }
        if (tokens.take("LIKE")) { sum(); return; }
        if (negated) throw RecoveryEffectGate.unclassified();
    }

    private void in() throws SQLException {
        tokens.need("(");
        if (tokens.at("SELECT")) RecoverySelectSql.query(tokens);
        else { do { read(); } while (tokens.take(",")); }
        tokens.need(")");
    }

    private void sum() throws SQLException {
        product();
        while (tokens.take("+") || tokens.take("-")) product();
    }

    private void product() throws SQLException {
        atom();
        while (tokens.take("*") || tokens.take("/")) atom();
    }

    private void atom() throws SQLException {
        while (tokens.take("-") || tokens.take("+")) { /* signed scalar */ }
        if (tokens.take("(")) {
            if (tokens.at("SELECT")) RecoverySelectSql.query(tokens); else read();
            tokens.need(")"); return;
        }
        if (tokens.take("*")) return;
        if (tokens.isLiteral()) { tokens.literal(); return; }
        if (tokens.take("CAST")) { cast(); return; }
        if (tokens.take("CASE")) { conditional(); return; }
        if (tokens.take("INTERVAL")) { interval(); return; }
        String name = tokens.name();
        if (tokens.take("(")) { function(name); return; }
        if (tokens.take(".")) { if (!tokens.take("*")) tokens.name(); }
        if (tokens.take("->>") || tokens.take("->")) tokens.need("?");
    }

    private void function(String name) throws SQLException {
        if (name.equals("DATABASE")) { tokens.need(")"); return; }
        if (!FUNCTIONS.contains(name)) throw RecoveryEffectGate.unclassified();
        if (tokens.take(")")) { if (!name.equals("UTC_TIMESTAMP")) throw RecoveryEffectGate.unclassified(); return; }
        if (name.equals("COUNT")) tokens.take("DISTINCT");
        do { read(); } while (tokens.take(","));
        tokens.need(")");
    }

    private void cast() throws SQLException {
        tokens.need("("); read(); tokens.need("AS");
        if (!TYPES.contains(tokens.peek())) throw RecoveryEffectGate.unclassified();
        tokens.need(tokens.peek());
        if (tokens.take("(")) { tokens.limit(); if (tokens.take(",")) tokens.limit(); tokens.need(")"); }
        tokens.need(")");
    }

    private void conditional() throws SQLException {
        tokens.need("WHEN"); read(); tokens.need("THEN"); read();
        while (tokens.take("WHEN")) { read(); tokens.need("THEN"); read(); }
        if (tokens.take("ELSE")) read();
        tokens.need("END");
    }

    private void interval() throws SQLException {
        tokens.limit();
        if (!UNITS.contains(tokens.peek())) throw RecoveryEffectGate.unclassified();
        tokens.need(tokens.peek());
    }
}
