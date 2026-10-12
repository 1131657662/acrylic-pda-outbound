package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual database inventory must retain case/approval/attachment references within the observed warehouse. */
public final class ReconciliationRecoveryCoverage {
    public static void verify(WebApplicationContext context, String warehouse, String caseId) {
        var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        var tables = tx.execute(status -> new RecoveryReleaseInventoryRows(context.getBean(JdbcTemplate.class), UUID.fromString(warehouse)).read());
        String encoded = Base64.getEncoder().encodeToString(com.acrylic.outbound.common.Ids.bytes(UUID.fromString(caseId)));
        for (String table : List.of("reconciliation_request","reconciliation_evidence","reconciliation_attachment_use","returned_stock_release")) {
            var found = new ArrayList<String>();
            int column = caseColumn(tables.path(table));
            for (var row : tables.at("/"+table+"/rows")) found.add(row.path(column).asText());
            assertThat(found).as("preserved %s case reference",table).contains(encoded);
        }
        var other = UUID.randomUUID();
        var foreign = tx.execute(status -> new RecoveryReleaseInventoryRows(context.getBean(JdbcTemplate.class),other).read());
        for (String table : List.of("reconciliation_request","reconciliation_evidence","reconciliation_attachment_use","reconciliation_posting","returned_stock_release"))
            assertThat(foreign.at("/"+table+"/rows").size()).as("warehouse isolation %s",table).isZero();
    }
    private static int caseColumn(JsonNode table) {
        int index = 0;
        for (var column : table.path("columns")) { if (column.asText().equals("case_id")) return index; index++; }
        throw new AssertionError("case reference column missing");
    }
    private ReconciliationRecoveryCoverage() { }
}
