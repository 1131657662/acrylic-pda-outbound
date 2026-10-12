package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.assertThat;

/** Original archived nonempty database and receipt bytes, never replaced with a freshly generated fixture. */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aReturnedStockHistoryIT {
    @Test void returnedStockMigrationPreservesAllHistoricalColumnsAndSignedReceipts() throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connectHistoricalHeader();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        var originals=new CorrectionAccountingMigrationOriginals(jdbc,profile.attempt);
        var before=originals.snapshot("reconcile-history-before");
        assertThat(before.get("`correction_command_receipt`")).isNotEmpty();
        assertThat(before.get("`dispatch_record`")).isNotEmpty();
        assertThat(before.get("`inventory_ledger`")).isNotEmpty();
        Flyway.configure().dataSource(jdbc.getDataSource()).target("86").load().migrate();
        var previous=originals.snapshot("reconcile-history-previous-schema");
        previousPermissions(jdbc,before,previous);
        originals=new CorrectionAccountingMigrationOriginals(jdbc,profile.attempt);
        var sliceBefore=originals.snapshot("reconcile-slice-before");
        var migration=Flyway.configure().dataSource(jdbc.getDataSource()).load();migration.migrate();migration.validate();
        assertThat(originals.snapshot("reconcile-history-after")).isEqualTo(sliceBefore);
        for(String table:new String[]{"reconciliation_request","reconciliation_evidence","reconciliation_attachment_use","reconciliation_posting","returned_stock_release"})
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class)).isZero();
        migration.migrate();assertThat(originals.snapshot("reconcile-history-repeated")).isEqualTo(sliceBefore);
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Original archived nonempty database, all preexisting typed columns and original receipt bytes preserved; no fabricated reconciliation request/evidence/posting/returned-stock release; Flyway repeat/validate passed");
    }

    private void previousPermissions(org.springframework.jdbc.core.JdbcTemplate jdbc,
        java.util.Map<String,java.util.List<String>> before,java.util.Map<String,java.util.List<String>> previous) {
        for(var entry:before.entrySet()) {
            if(java.util.Set.of("`permission`","`role_permission`").contains(entry.getKey()))
                assertThat(previous.get(entry.getKey())).containsAll(entry.getValue()).hasSize(entry.getValue().size()+2);
            else assertThat(previous.get(entry.getKey())).as("unchanged historical table %s",entry.getKey()).isEqualTo(entry.getValue());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM permission WHERE code IN ('rescue:authorize','rescue:read')",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_permission rp JOIN permission p ON p.id=rp.permission_id"
            +" JOIN role r ON r.id=rp.role_id WHERE p.code IN ('rescue:authorize','rescue:read') AND r.code='SUPERVISOR'",Integer.class)).isEqualTo(2);
    }
}
