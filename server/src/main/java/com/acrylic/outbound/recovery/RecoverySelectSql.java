package com.acrylic.outbound.recovery;

import java.sql.SQLException;

/** Recognises SELECT syntax only; even views and locking reads always remain SOURCE_READ. */
final class RecoverySelectSql {
    private final RecoverySqlTokens tokens;
    private final RecoverySqlExpression expression;

    private RecoverySelectSql(RecoverySqlTokens tokens) {
        this.tokens = tokens;
        expression = new RecoverySqlExpression(tokens);
    }

    static void require(String text) throws SQLException {
        var tokens = new RecoverySqlTokens(text);
        query(tokens);
        if (!tokens.done()) throw RecoveryEffectGate.unclassified();
    }

    static void query(RecoverySqlTokens tokens) throws SQLException {
        tokens.enter();
        try { new RecoverySelectSql(tokens).select(); }
        finally { tokens.leave(); }
    }

    private void select() throws SQLException {
        tokens.need("SELECT"); tokens.take("DISTINCT");
        do { expression.read(); alias(); } while (tokens.take(","));
        if (tokens.take("FROM")) from();
        if (tokens.take("WHERE")) expression.read();
        if (tokens.take("GROUP")) { tokens.need("BY"); list(); }
        if (tokens.take("HAVING")) expression.read();
        if (tokens.take("ORDER")) order();
        if (tokens.take("LIMIT")) limit();
        if (tokens.take("FOR")) locking();
    }

    private void from() throws SQLException {
        table();
        while (tokens.at("JOIN") || tokens.at("INNER") || tokens.at("LEFT") || tokens.at("RIGHT")) {
            if (!tokens.at("JOIN")) {
                if (!tokens.take("INNER")) { if (!tokens.take("LEFT")) tokens.need("RIGHT"); tokens.take("OUTER"); }
            }
            tokens.need("JOIN"); table(); tokens.need("ON"); expression.read();
        }
    }

    private void table() throws SQLException {
        tokens.name();
        if (tokens.take(".")) tokens.name();
        alias();
    }

    private void alias() throws SQLException {
        if (tokens.take("AS")) tokens.name();
        else if (tokens.identifier()) tokens.name();
    }

    private void list() throws SQLException { do { expression.read(); } while (tokens.take(",")); }

    private void order() throws SQLException {
        tokens.need("BY");
        do { expression.read(); if (!tokens.take("ASC")) tokens.take("DESC"); } while (tokens.take(","));
    }

    private void limit() throws SQLException {
        tokens.limit();
        if (tokens.take(",") || tokens.take("OFFSET")) tokens.limit();
    }

    private void locking() throws SQLException {
        if (tokens.take("UPDATE")) { if (tokens.take("SKIP")) tokens.need("LOCKED"); }
        else tokens.need("SHARE");
    }
}
