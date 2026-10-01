package com.acrylic.outbound.exports;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Traces each withdrawal to its own forward posting through immutable restoration chains. */
final class CorrectionWithdrawalLedgerProof {
    private final JdbcTemplate jdbc;
    CorrectionWithdrawalLedgerProof(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    String read(Map<String, Object> row) {
        String line = row.get("line_id").toString();
        var all = jdbc.queryForList("""
            SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(correction_line_id) line_id,entry_kind,quantity_delta,weight_delta_g,
              BIN_TO_UUID(COALESCE(restores_entry_id,reverses_entry_id,reverses_inventory_entry_id)) source_id,
              BIN_TO_UUID(restores_entry_id) restores_id
            FROM correction_ledger WHERE dispatch_id=UUID_TO_BIN(?) ORDER BY id
            """, row.get("dispatch_id"));
        var byId = new HashMap<String, Map<String, Object>>();
        var forward = new HashSet<String>();
        for (var entry : all) {
            byId.put(entry.get("id").toString(), entry);
            if (line.equals(entry.get("line_id")) && entry.get("restores_id") == null) forward.add(entry.get("id").toString());
        }
        ExportFacts.check(forward.size() == number(row, "ledger_rows"), "撤销恢复账的原贡献数量不符");
        var refs = new ArrayList<String>(); var proven = new HashSet<String>();
        for (var entry : all) if (line.equals(entry.get("line_id")) && entry.get("restores_id") != null)
            refs.add(prove(entry, byId, forward, proven));
        ExportFacts.check(proven.equals(forward), "撤销恢复账不完整");
        return refs.isEmpty() ? "ledger_rows:0" : String.join(" / ", refs);
    }

    private String prove(Map<String, Object> restored, Map<String, Map<String, Object>> byId,
        Set<String> forward, Set<String> proven) {
        var source = byId.get(restored.get("restores_id").toString());
        ExportFacts.check(source != null && "WITHDRAWAL_RESTORE".equals(restored.get("entry_kind")), "撤销恢复账缺失");
        ExportFacts.check(number(restored, "quantity_delta") == Math.negateExact(number(source, "quantity_delta"))
            && number(restored, "weight_delta_g") == Math.negateExact(number(source, "weight_delta_g")), "撤销账未逐笔恢复");
        var origin = ancestor(source, byId, forward);
        ExportFacts.check(origin != null && proven.add(origin), "撤销账原贡献重复或引用不符");
        return "withdrawal_ledger:" + restored.get("id") + " restores:" + source.get("id") + " forward:" + origin;
    }

    private String ancestor(Map<String, Object> entry, Map<String, Map<String, Object>> byId, Set<String> forward) {
        var visited = new HashSet<String>();
        while (entry != null) {
            String id = entry.get("id").toString();
            ExportFacts.check(visited.add(id), "撤销账引用成环");
            if (forward.contains(id)) return id;
            entry = entry.get("source_id") == null ? null : byId.get(entry.get("source_id").toString());
        }
        return null;
    }
    private static long number(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
}
