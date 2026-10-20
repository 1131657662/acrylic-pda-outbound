package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Fixed read of four independent facts; no joined ledger fan-out or inferred physical availability. */
@Component
final class ReconciliationFacts {
    private final JdbcTemplate jdbc;
    private final CorrectionStore store;
    private final CorrectionHeaderOrigin original;
    ReconciliationFacts(JdbcTemplate jdbc, CorrectionStore store, CorrectionHeaderOrigin original) {
        this.jdbc = jdbc; this.store = store; this.original = original;
    }

    UUID order(UUID dispatch) {
        var rows = jdbc.queryForList("SELECT BIN_TO_UUID(order_id) id FROM dispatch_record WHERE id=?", String.class, Ids.bytes(dispatch));
        if (rows.size() != 1) throw CorrectionCommand.missing();
        return UUID.fromString(rows.getFirst());
    }

    void lock(UUID dispatch) {
        var rows = jdbc.queryForList("SELECT bale_id FROM dispatch_record WHERE id=? FOR UPDATE", byte[].class, Ids.bytes(dispatch));
        if (rows.size() != 1) throw CorrectionCommand.missing();
        byte[] bale = rows.getFirst();
        jdbc.queryForList("SELECT id FROM bale WHERE id=? FOR UPDATE", byte[].class, bale);
        jdbc.queryForList("SELECT bale_id FROM inventory_position WHERE bale_id=? FOR UPDATE", byte[].class, bale);
        jdbc.queryForList("SELECT bale_id FROM bale_commitment WHERE bale_id=? FOR UPDATE", byte[].class, bale);
        jdbc.queryForList("SELECT id FROM inventory_ledger WHERE dispatch_id=? ORDER BY id FOR UPDATE", byte[].class, Ids.bytes(dispatch));
        jdbc.queryForList("SELECT id FROM correction_ledger WHERE dispatch_id=? ORDER BY id FOR UPDATE", byte[].class, Ids.bytes(dispatch));
    }

    ObjectNode read(UUID dispatch) {
        UUID order = order(dispatch); var result = SubmissionJson.object();
        var shipment = store.dispatchFacts(order, dispatch);
        UUID bale = UUID.fromString(shipment.path("baleId").asText());
        result.set("result", store.orderFacts(order)); result.set("dispatch", shipment);
        result.set("originalHeader", original.base(order)); result.set("stock", stock(bale));
        result.set("owners", owners(bale)); result.set("ledger", ledger(dispatch));
        return result;
    }

    private JsonNode stock(UUID bale) {
        return single("""
            SELECT JSON_OBJECT('baleId',BIN_TO_UUID(b.id),'warehouseId',BIN_TO_UUID(b.warehouse_id),
              'dispatchCycle',CAST(b.dispatch_cycle AS CHAR),'snapshotId',BIN_TO_UUID(p.latest_snapshot_id),
              'lastSeenSnapshotId',BIN_TO_UUID(p.last_seen_snapshot_id),'activeSnapshotId',BIN_TO_UUID(w.active_inventory_snapshot_id),
              'upstreamPresent',p.upstream_present,'bookStatus',p.book_status,'blockedReason',p.blocked_reason,
              'stockVersion',CAST(p.row_version AS CHAR),'barcode',b.barcode)
            FROM bale b JOIN warehouse w ON w.id=b.warehouse_id JOIN inventory_position p ON p.bale_id=b.id WHERE b.id=?
            """, bale);
    }

    private JsonNode owners(UUID bale) {
        var result = SubmissionJson.object().putArray("owners");
        jdbc.queryForList("""
            SELECT JSON_OBJECT('orderId',BIN_TO_UUID(order_id),'cycle',CAST(dispatch_cycle AS CHAR),'state',state) data
            FROM bale_commitment WHERE bale_id=? ORDER BY order_id
            """, String.class, Ids.bytes(bale)).forEach(row -> result.add(SubmissionJson.parse(row)));
        return result;
    }

    private JsonNode ledger(UUID dispatch) {
        var result = SubmissionJson.object().putArray("entries");
        jdbc.queryForList("""
            SELECT JSON_OBJECT('id',BIN_TO_UUID(id),'kind','ORIGINAL','baleId',BIN_TO_UUID(bale_id),
              'quantityDelta',CAST(quantity_delta AS CHAR),'weightDeltaG',CAST(weight_delta_g AS CHAR)) data
            FROM inventory_ledger WHERE dispatch_id=? ORDER BY id
            """, String.class, Ids.bytes(dispatch)).forEach(row -> result.add(SubmissionJson.parse(row)));
        jdbc.queryForList("""
            SELECT JSON_OBJECT('id',BIN_TO_UUID(id),'kind',entry_kind,'baleId',BIN_TO_UUID(bale_id),
              'quantityDelta',CAST(quantity_delta AS CHAR),'weightDeltaG',CAST(weight_delta_g AS CHAR),
              'lineId',BIN_TO_UUID(correction_line_id),'reversesEntryId',BIN_TO_UUID(reverses_entry_id),
              'reversesInventoryEntryId',BIN_TO_UUID(reverses_inventory_entry_id),'restoresEntryId',BIN_TO_UUID(restores_entry_id)) data
            FROM correction_ledger WHERE dispatch_id=? ORDER BY id
            """, String.class, Ids.bytes(dispatch)).forEach(row -> result.add(SubmissionJson.parse(row)));
        return result;
    }

    private JsonNode single(String sql, UUID id) {
        var rows = jdbc.queryForList(sql, String.class, Ids.bytes(id));
        if (rows.size() != 1) throw CorrectionCommand.corrupt();
        return SubmissionJson.parse(rows.getFirst());
    }

    static String hash(JsonNode facts) { return CorrectionCommand.sha(CorrectionReceiptCodec.bytes(facts)); }
}
