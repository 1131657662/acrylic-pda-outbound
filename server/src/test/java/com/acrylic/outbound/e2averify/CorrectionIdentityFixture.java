package com.acrylic.outbound.e2averify;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** Deliberately childless SQL fixtures isolate identity guards from foreign-key rejection. */
final class CorrectionIdentityFixture {
    final JdbcTemplate jdbc;
    final String document = UUID.randomUUID().toString(), reconciliation = UUID.randomUUID().toString();
    final String actor;

    CorrectionIdentityFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        var host = jdbc.queryForMap("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(warehouse_id) warehouse FROM outbound_order ORDER BY id LIMIT 1");
        actor = jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM app_user ORDER BY id LIMIT 1", String.class);
        jdbc.update("INSERT INTO correction_document(id,warehouse_id,order_id,type,expected_result_version,reason,"
            + "document_sha256,status,source_recovery_epoch,row_version,requested_by,requested_at) VALUES(UUID_TO_BIN(?),"
            + "UUID_TO_BIN(?),UUID_TO_BIN(?),'HEADER_CORRECTION',1,'childless identity probe',REPEAT('a',64),"
            + "'SUBMITTED',1,1,UUID_TO_BIN(?),'2026-10-01 00:01:02.123')",
            document, host.get("warehouse"), host.get("id"), actor);
        jdbc.update("INSERT INTO inventory_reconciliation(id,warehouse_id,kind,status,expected_result_version,"
            + "expected_ledger_version,expected_stock_state_version,row_version,created_at,updated_at) VALUES(UUID_TO_BIN(?),"
            + "UUID_TO_BIN(?),'STOCK_MISMATCH','OPEN',1,1,1,1,'2026-10-01 00:01:02.123','2026-10-01 00:01:02.123')",
            reconciliation, host.get("warehouse"));
    }

    void oldSchemaAllowsIdentityChange() {
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        transaction.executeWithoutResult(status -> {
            changeIdentity("correction_document", document);
            changeIdentity("inventory_reconciliation", reconciliation);
            status.setRollbackOnly();
        });
    }

    private void changeIdentity(String table, String original) {
        String changed = UUID.randomUUID().toString();
        assertThat(jdbc.update("UPDATE " + table + " SET id=UUID_TO_BIN(?) WHERE id=UUID_TO_BIN(?)", changed, original))
            .as("control: earlier schema permits changing a childless original ID").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id=UUID_TO_BIN(?)", Integer.class, changed)).isEqualTo(1);
    }
}
