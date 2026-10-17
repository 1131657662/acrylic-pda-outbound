package com.acrylic.outbound.recovery;

import java.sql.SQLException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Deliberately small grammar. Unrecognised SQL is rejected, never delegated on a guess. */
final class RecoveryEffectSql {
    enum Kind { READ, SOURCE_READ, WRITE, COMMIT, ROLLBACK, BEGIN, AUTO_ON, DDL }
    private static final String ID = "(?:[A-Z_][A-Z_0-9]*|`[A-Z_][A-Z_0-9]*`)(?:\\.(?:[A-Z_][A-Z_0-9]*|`[A-Z_][A-Z_0-9]*`))?";
    private static final String VALUE = "(?:\\?|[0-9]+|NULL|TRUE|FALSE)";
    private static final Pattern CONSTANT = Pattern.compile("SELECT " + VALUE);

    private RecoveryEffectSql() { }

    static Kind classify(String sql) throws SQLException {
        String text = normalise(sql);
        if (text.equals("COMMIT") || text.equals("COMMIT WORK")) return Kind.COMMIT;
        if (text.matches("SET AUTOCOMMIT\\s*=\\s*1")) return Kind.AUTO_ON;
        if (text.equals("BEGIN") || text.equals("START TRANSACTION")) return Kind.BEGIN;
        if (text.matches("ROLLBACK(?: WORK)?")) return Kind.ROLLBACK;
        if (text.matches("ROLLBACK TO(?: SAVEPOINT)? " + ID)) return Kind.READ;
        return statement(text);
    }

    private static Kind statement(String text) throws SQLException {
        // A relation can be a view invoking a mutating stored function. Syntax is not read-only proof.
        if (CONSTANT.matcher(text).matches()) return Kind.READ;
        if (text.startsWith("SELECT ")) { RecoverySelectSql.require(text); return Kind.SOURCE_READ; }
        if (text.matches("SHOW (?:TABLES|DATABASES)")) return Kind.READ;
        if (text.matches("(?:DESC|DESCRIBE) " + ID)) return Kind.SOURCE_READ;
        if (text.startsWith("EXPLAIN SELECT ")) { RecoverySelectSql.require(text.substring(8)); return Kind.SOURCE_READ; }
        if (text.matches("(?:INSERT INTO|REPLACE INTO|MERGE INTO|DELETE FROM|UPDATE) " + ID + "(?:[ (].*)?"))
            return Kind.WRITE;
        if (text.matches("(?:CREATE TABLE|ALTER TABLE|DROP TABLE|TRUNCATE TABLE) " + ID + "(?:[ (].*)?"))
            return Kind.DDL;
        // WITH, procedural DDL and unrecognised dialects need a proven grammar first.
        throw RecoveryEffectGate.unclassified();
    }

    private static String normalise(String sql) throws SQLException {
        if (sql == null || sql.isBlank() || sql.length() > 65536) throw RecoveryEffectGate.unclassified();
        if (sql.contains(";") || sql.contains("/*") || sql.contains("--") || sql.contains("#"))
            throw RecoveryEffectGate.unclassified();
        // Backslash/double-quote interpretation depends on server SQL_MODE: do not guess.
        if (sql.contains("\\") || sql.contains("\"") || sql.indexOf('\0') >= 0)
            throw RecoveryEffectGate.unclassified();
        String masked = maskStrings(sql);
        // Case folding must not turn a Unicode stored-function name into an allowed ASCII builtin.
        if (masked.chars().anyMatch(ch -> ch > 127)) throw RecoveryEffectGate.unclassified();
        return masked.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private static String maskStrings(String sql) throws SQLException {
        StringBuilder result = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (ch == '\'') {
                if (quoted && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') { i++; continue; }
                quoted = !quoted;
                if (quoted) result.append('?');
            } else if (!quoted) result.append(ch);
        }
        if (quoted) throw RecoveryEffectGate.unclassified();
        return result.toString();
    }
}
