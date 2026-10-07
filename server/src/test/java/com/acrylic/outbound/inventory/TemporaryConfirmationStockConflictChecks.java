package com.acrylic.outbound.inventory;

import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static com.acrylic.outbound.inventory.TemporaryConfirmationChecks.reject;

final class TemporaryConfirmationStockConflictChecks {
    private final WebApplicationContext context;

    TemporaryConfirmationStockConflictChecks(WebApplicationContext context) {
        this.context = context;
    }

    void verify() throws Exception {
        supervisorApprovalDoesNotBypassCurrentInventoryConflict();
    }

    private void supervisorApprovalDoesNotBypassCurrentInventoryConflict() throws Exception {
        var fixture = new TemporaryConfirmationFixture(context, true);
        var operator = fixture.operator();
        var supervisor = fixture.supervisor();
        var approval = fixture.command("SHORT_APPROVE", supervisor);
        var approvalReceipt = fixture.execute(approval, supervisor);
        String bale = fixture.source.body.at("/scans/0/baleId").asText();
        assertThat(fixture.jdbc.update("UPDATE inventory_position SET blocked_reason=? WHERE bale_id=UUID_TO_BIN(?)",
            "D05_ISOLATED_HOLD", bale)).isEqualTo(1);

        var before = fixture.state();
        reject("TEMPORARY_STOCK_CONFLICT", () -> fixture.execute(fixture.command("NORMAL_CONFIRM", operator), operator));

        assertThat(fixture.state()).isEqualTo(before);
        assertThat(fixture.jdbc.queryForObject("SELECT blocked_reason FROM inventory_position WHERE bale_id=UUID_TO_BIN(?)",
            String.class, bale)).isEqualTo("D05_ISOLATED_HOLD");
        assertThat(fixture.jdbc.queryForObject("SELECT status FROM submission WHERE id=UUID_TO_BIN(?)",
            String.class, fixture.source.id())).isEqualTo("REVIEW_REQUIRED");
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_record WHERE submission_id=UUID_TO_BIN(?)",
            Integer.class, fixture.source.id())).isZero();
        assertThat(fixture.execute(approval, supervisor)).isEqualTo(approvalReceipt);
    }
}
