package com.acrylic.outbound.inventory;

import com.acrylic.outbound.relay.RelayArchive;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** Explicit synthetic stock and target; original native source/credential/Keystore facts stay intact. */
final class IndependentCheckpointTarget {
    private IndependentCheckpointTarget() { }

    static UUID create(JdbcTemplate jdbc, RelayArchive.Bundle original, JsonNode inventory) {
        String warehouse = original.original().warehouse().toString();
        String snapshot = jdbc.queryForObject("SELECT BIN_TO_UUID(active_inventory_snapshot_id) FROM warehouse WHERE id=UUID_TO_BIN(?)", String.class, warehouse);
        for (var capture : inventory.path("items")) stock(jdbc, warehouse, snapshot, capture.path("original"));
        UUID order = UUID.randomUUID(), revision = UUID.randomUUID();
        var task = original.independent().path("task");
        Timestamp at = Timestamp.from(Instant.parse(task.path("createdTrustedAt").asText()));
        jdbc.update("INSERT INTO outbound_order(id,warehouse_id,order_no,source,status,current_revision,row_version,created_by,created_at,updated_at)"
            + " VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,'WEB','DRAFT',NULL,1,UUID_TO_BIN(?),?,?)",
            order.toString(), warehouse, "I-" + order, original.original().user().toString(), at, at);
        jdbc.update("INSERT INTO order_revision VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),1,UUID_TO_BIN(?),"
            + "(SELECT mapping_revision FROM warehouse WHERE id=UUID_TO_BIN(?)),?,'synthetic','isolated full target',?,'fixture',UUID_TO_BIN(?),?)",
            revision.toString(), order.toString(), warehouse, snapshot, warehouse, task.path("vehicleNo").asText(),
            inventory.path("items").size(), original.original().user().toString(), at);
        var row = inventory.path("items").get(0).path("original");
        jdbc.update("INSERT INTO order_line SELECT UUID_TO_BIN(?),delivery_code,id,product_id,?,?,? FROM delivery_mapping"
            + " WHERE warehouse_id=UUID_TO_BIN(?) AND delivery_code=? AND is_active=1 AND enabled=1",
            revision.toString(), row.path("model").asText(), row.path("name").asText(), inventory.path("items").size(),
            warehouse, row.path("deliveryCode").asText());
        jdbc.update("UPDATE outbound_order SET current_revision=1 WHERE id=UUID_TO_BIN(?)", order.toString());
        return order;
    }

    private static void stock(JdbcTemplate jdbc, String warehouse, String snapshot, JsonNode row) {
        String barcode = row.path("barcode").asText();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bale WHERE barcode=? AND warehouse_id=UUID_TO_BIN(?)", Integer.class, barcode, warehouse)).isZero();
        String product = jdbc.queryForObject("SELECT BIN_TO_UUID(product_id) FROM delivery_mapping WHERE warehouse_id=UUID_TO_BIN(?)"
            + " AND delivery_code=? AND is_active=1 AND enabled=1", String.class, warehouse, row.path("deliveryCode").asText());
        String bale = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO bale VALUES(UUID_TO_BIN(?),?,UUID_TO_BIN(?),1,UTC_TIMESTAMP(3))", bale, barcode, warehouse);
        jdbc.update("INSERT INTO inventory_snapshot_bale VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,UUID_TO_BIN(?),?,?,?,?)",
            snapshot, bale, row.path("deliveryCode").asText(), product, row.path("model").asText(), row.path("name").asText(),
            row.path("weightGrams").asLong(), row.path("rawWeight").asText());
        jdbc.update("INSERT INTO inventory_position VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),1,'NOT_DEDUCTED',NULL,1)", bale, snapshot, snapshot);
    }
}
