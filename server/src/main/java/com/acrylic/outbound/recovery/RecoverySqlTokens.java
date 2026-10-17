package com.acrylic.outbound.recovery;

import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;

/** Tokens for the bounded SELECT grammar, after literals have been masked by RecoveryEffectSql. */
final class RecoverySqlTokens {
    private static final Pattern TOKEN = Pattern.compile(
        "`[A-Z_][A-Z_0-9]*`|[A-Z_][A-Z_0-9]*|[0-9]+|->>|->|<=|>=|<>|!=|[?(),.*+/<>=-]");
    private static final Set<String> RESERVED = Set.of(
        "SELECT", "FROM", "WHERE", "JOIN", "INNER", "LEFT", "RIGHT", "OUTER", "ON", "AS", "DISTINCT",
        "GROUP", "BY", "HAVING", "ORDER", "ASC", "DESC", "LIMIT", "OFFSET", "FOR", "UPDATE", "SHARE",
        "SKIP", "LOCKED", "BETWEEN", "AND", "OR", "NOT", "IN", "IS", "LIKE", "EXISTS", "NULL", "TRUE",
        "FALSE", "CAST", "CASE", "WHEN", "THEN", "ELSE", "END", "INTERVAL", "INTO", "OUTFILE", "DUMPFILE",
        "UNION", "WITH", "PROCEDURE", "LOCK", "SET", "CALL", "DELETE", "INSERT");
    private final List<String> tokens = new ArrayList<>();
    private int position;
    private int depth;

    RecoverySqlTokens(String text) throws SQLException {
        var matcher = TOKEN.matcher(text);
        int offset = 0;
        while (offset < text.length()) {
            if (text.charAt(offset) == ' ') { offset++; continue; }
            matcher.region(offset, text.length());
            if (!matcher.lookingAt() || tokens.size() >= 8192) throw RecoveryEffectGate.unclassified();
            tokens.add(matcher.group());
            offset = matcher.end();
        }
    }

    String peek() { return position < tokens.size() ? tokens.get(position) : ""; }
    boolean at(String value) { return peek().equals(value); }
    boolean done() { return position == tokens.size(); }
    boolean take(String value) {
        if (!at(value)) return false;
        position++;
        return true;
    }

    void need(String value) throws SQLException { if (!take(value)) throw RecoveryEffectGate.unclassified(); }

    boolean identifier() { return peek().matches("`[A-Z_][A-Z_0-9]*`|[A-Z_][A-Z_0-9]*") && !RESERVED.contains(peek()); }

    String name() throws SQLException {
        if (!identifier()) throw RecoveryEffectGate.unclassified();
        return tokens.get(position++);
    }

    void literal() throws SQLException {
        if (!peek().matches("[0-9]+|\\?|NULL|TRUE|FALSE")) throw RecoveryEffectGate.unclassified();
        position++;
    }

    boolean isLiteral() { return peek().matches("[0-9]+|\\?|NULL|TRUE|FALSE"); }

    void limit() throws SQLException {
        if (!peek().matches("[0-9]+|\\?")) throw RecoveryEffectGate.unclassified();
        position++;
    }

    void enter() throws SQLException { if (++depth > 32) throw RecoveryEffectGate.unclassified(); }
    void leave() { depth--; }
}
