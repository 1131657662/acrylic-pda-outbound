package com.acrylic.outbound.e2averify;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** Real confirmed dispatch plus retained postings created using the pre-reference schema. */
final class CorrectionAccountingMigrationFixture {
    final JdbcTemplate jdbc;
    final String dispatch, bale, inventory;
    final String document = UUID.randomUUID().toString(), line = UUID.randomUUID().toString();
    final String oldWeight = UUID.randomUUID().toString(), oldReversal = UUID.randomUUID().toString();

    CorrectionAccountingMigrationFixture(E2aCrossSourceProfile profile, JdbcTemplate jdbc) throws Exception {
        this.jdbc = jdbc;
        var order = new E2aCorrectionChainIT().confirmedOrder(profile, jdbc);
        dispatch = order.dispatchIds().getFirst();
        bale = jdbc.queryForObject("SELECT BIN_TO_UUID(bale_id) FROM dispatch_record WHERE id=UUID_TO_BIN(?)", String.class, dispatch);
        inventory = jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM inventory_ledger WHERE dispatch_id=UUID_TO_BIN(?)", String.class, dispatch);
        assertThat(inventory).isNotBlank();
        seedDocument(order); seedLine();
        oldPosting(oldWeight, "WEIGHT_DIFF", 0, -7);
        oldPosting(oldReversal, "REVERSAL", -1, -11);
    }

    private void seedDocument(E2aCorrectionChainIT.Order order) {
        jdbc.update("INSERT INTO correction_document(id,warehouse_id,order_id,type,expected_result_version,reason,"
            + "document_sha256,status,source_recovery_epoch,row_version,requested_by,requested_at) VALUES(UUID_TO_BIN(?),"
            + "UUID_TO_BIN(?),UUID_TO_BIN(?),'WEIGHT_CORRECTION',?,'nonempty migration probe',REPEAT('a',64),"
            + "'SUBMITTED',1,1,UUID_TO_BIN(?),UTC_TIMESTAMP(3))", document, order.warehouseId(), order.orderId(),
            Long.parseLong(order.resultVersion()), order.operatorId().toString());
    }

    private void seedLine() {
        jdbc.update("INSERT INTO correction_line(id,document_id,line_no,dispatch_id,bale_id,delivery_code,before_count,"
            + "before_weight_g,target_count,target_weight_g,business_occurred_at) SELECT UUID_TO_BIN(?),UUID_TO_BIN(?),"
            + "1,id,bale_id,delivery_code,1,weight_g,1,weight_g+7,UTC_TIMESTAMP(3) FROM dispatch_record WHERE id=UUID_TO_BIN(?)",
            line, document, dispatch);
    }

    private void oldPosting(String id, String kind, int quantity, long weight) {
        jdbc.update("INSERT INTO correction_ledger(id,dispatch_id,bale_id,correction_line_id,entry_kind,quantity_delta,"
            + "weight_delta_g,business_key,created_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),"
            + "?,?,?,CONCAT('MIGRATION:',?),UTC_TIMESTAMP(3))", id, dispatch, bale, line, kind, quantity, weight, id);
    }

    String insert(String kind, String reverse, String reverseInventory, String restore) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO correction_ledger(id,dispatch_id,bale_id,correction_line_id,entry_kind,quantity_delta,"
            + "weight_delta_g,reverses_entry_id,reverses_inventory_entry_id,restores_entry_id,business_key,created_at)"
            + " VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,0,7,UUID_TO_BIN(?),UUID_TO_BIN(?),"
            + "UUID_TO_BIN(?),CONCAT('MIGRATION:',?),UTC_TIMESTAMP(3))", id, dispatch, bale, line, kind, reverse, reverseInventory, restore, id);
        return id;
    }
}
