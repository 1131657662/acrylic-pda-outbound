package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Posts only validated actual contributions, while the caller holds the order and dispatch locks. */
@Component
public class CorrectionAccounting {
    private final JdbcTemplate jdbc;
    public CorrectionAccounting(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<String> approve(Map<String, Object> line, UUID bale, Instant now) {
        var dispatch = UUID.fromString(line.get("dispatch_id").toString());
        var active = CorrectionContribution.active(entries(dispatch), bale, values(line, "before"));
        long targetCount = number(line, "target_count"), target = number(line, "target_weight_g");
        if (number(line, "before_count") != 1 || targetCount < 0 || targetCount > 1
            || targetCount == 0 && target != 0 || targetCount == 1 && target <= 0) throw CorrectionCommand.ledgerConflict();
        if (active.isEmpty()) return List.of();
        var lineId = UUID.fromString(line.get("line_id").toString());
        if (targetCount == 0) return reverse(active, dispatch, lineId, now);
        long delta = Math.subtractExact(number(line, "before_weight_g"), target);
        if (delta == 0) return List.of();
        String key = "CORR:" + lineId + ":WD";
        insert(dispatch, new Posting(bale, lineId, "WEIGHT_DIFF", 0, delta, null, null, null, key), now);
        return List.of(key);
    }

    public ArrayNode withdraw(List<Map<String, Object>> lines, Instant now) {
        var restored = SubmissionJson.object().putArray("entries");
        for (var line : lines) {
            if (line.get("dispatch_id") == null) continue;
            var dispatch = UUID.fromString(line.get("dispatch_id").toString());
            var all = entries(dispatch);
            UUID bale = jdbc.queryForObject("SELECT BIN_TO_UUID(bale_id) FROM dispatch_record WHERE id=?",
                (rs, n) -> UUID.fromString(rs.getString(1)), Ids.bytes(dispatch));
            CorrectionContribution.active(all, bale, values(line, "target"));
            var lineId = UUID.fromString(line.get("line_id").toString());
            var forward = all.stream().filter(entry -> lineId.equals(entry.line()) && entry.restores() == null).toList();
            for (var entry : forward) {
                var source = CorrectionContribution.representative(all, entry);
                restore(dispatch, source, lineId, restored, now);
            }
            CorrectionContribution.active(entries(dispatch), bale, values(line, "before"));
        }
        return restored;
    }

    private void restore(UUID dispatch, CorrectionContribution.Entry entry, UUID line, ArrayNode restored, Instant now) {
        String key = "WD:" + line + ":" + entry.id();
        long quantity = Math.negateExact(entry.quantity()), weight = Math.negateExact(entry.weight());
        insert(dispatch, new Posting(entry.bale(), line, "WITHDRAWAL_RESTORE", quantity, weight,
            null, null, entry.id(), key), now);
        restored.add(SubmissionJson.object().put("restoresEntryId", entry.id().toString()).put("businessKey", key)
            .put("quantityDelta", Long.toString(quantity)).put("weightDeltaG", Long.toString(weight)));
    }

    private List<CorrectionContribution.Entry> entries(UUID dispatch) {
        var result = new ArrayList<CorrectionContribution.Entry>();
        jdbc.query("SELECT BIN_TO_UUID(id),BIN_TO_UUID(bale_id),quantity_delta,weight_delta_g FROM inventory_ledger "
            + "WHERE dispatch_id=? ORDER BY id FOR UPDATE", rs -> {
                result.add(new CorrectionContribution.Entry(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)),
                    null, "ORIGINAL", rs.getLong(3), rs.getLong(4), null, null, null, true));
            }, Ids.bytes(dispatch));
        jdbc.query("SELECT BIN_TO_UUID(id),BIN_TO_UUID(bale_id),BIN_TO_UUID(correction_line_id),entry_kind,"
            + "quantity_delta,weight_delta_g,BIN_TO_UUID(reverses_entry_id),BIN_TO_UUID(reverses_inventory_entry_id),"
            + "BIN_TO_UUID(restores_entry_id) FROM correction_ledger WHERE dispatch_id=? ORDER BY id FOR UPDATE", rs -> {
                result.add(new CorrectionContribution.Entry(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)),
                    uuid(rs.getString(3)), rs.getString(4), rs.getLong(5), rs.getLong(6), uuid(rs.getString(7)),
                    uuid(rs.getString(8)), uuid(rs.getString(9)), false));
            }, Ids.bytes(dispatch));
        return result;
    }

    private List<String> reverse(List<CorrectionContribution.Entry> active, UUID dispatch, UUID line, Instant now) {
        var keys = new ArrayList<String>();
        for (var entry : active) {
            String key = "CORR:" + line + ":REV:" + entry.id();
            insert(dispatch, new Posting(entry.bale(), line, "REVERSAL", Math.negateExact(entry.quantity()),
                Math.negateExact(entry.weight()), entry.original() ? null : entry.id(),
                entry.original() ? entry.id() : null, null, key), now);
            keys.add(key);
        }
        return keys;
    }

    private void insert(UUID dispatch, Posting p, Instant now) {
        jdbc.update("""
            INSERT INTO correction_ledger(id,dispatch_id,bale_id,correction_line_id,entry_kind,quantity_delta,
              weight_delta_g,reverses_entry_id,reverses_inventory_entry_id,restores_entry_id,business_key,created_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
            """, Ids.bytes(Ids.next()), Ids.bytes(dispatch), Ids.bytes(p.bale()), Ids.bytes(p.line()), p.kind(),
            p.quantity(), p.weight(), bytes(p.reverse()), bytes(p.reverseInventory()), bytes(p.restore()), p.key(), Timestamp.from(now));
    }

    private record Posting(UUID bale, UUID line, String kind, long quantity, long weight,
        UUID reverse, UUID reverseInventory, UUID restore, String key) { }
    private static UUID uuid(String value) { return value == null ? null : UUID.fromString(value); }
    private static byte[] bytes(UUID id) { return id == null ? null : Ids.bytes(id); }
    private static long number(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    private static long[] values(Map<String, Object> row, String side) {
        return new long[]{number(row, side + "_count"), number(row, side + "_weight_g")};
    }
}
