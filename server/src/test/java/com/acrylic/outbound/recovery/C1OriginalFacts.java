package com.acrylic.outbound.recovery;

import java.security.MessageDigest;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Hashes every pre-existing table, including original request/receipt/signature bytes. */
final class C1OriginalFacts {
    private C1OriginalFacts() { }

    static Map<String, String> snapshot(JdbcTemplate jdbc) {
        var result = new TreeMap<String, String>();
        var tables = jdbc.queryForList("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() "
            + "AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME", String.class);
        for (String table : tables) {
            if (table.equals("recovery_initial_normal") || table.equals("recovery_entry_probe")) continue;
            if (!table.matches("[A-Za-z0-9_]+")) throw new AssertionError("Unsafe fixture table");
            var rows = jdbc.query("SELECT * FROM `" + table + "`", (rs, row) -> {
                var digest = digest();
                for (int col = 1; col <= rs.getMetaData().getColumnCount(); col++) {
                    byte[] bytes = rs.getBytes(col);
                    digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes == null ? -1 : bytes.length).array());
                    if (bytes != null) digest.update(bytes);
                }
                return HexFormat.of().formatHex(digest.digest());
            });
            Collections.sort(rows);
            result.put(table, rows.size() + ":" + HexFormat.of().formatHex(digest().digest(String.join("\n", rows)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        }
        return result;
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (Exception error) { throw new AssertionError(error); }
    }
}
