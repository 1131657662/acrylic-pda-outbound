package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import org.springframework.web.context.WebApplicationContext;

/** Synthetic confirmed TEMP facts prepared before admitted contexts; not a production command. */
public record RecoveryExportInput(String username, String warehouse, String order) {
    public static RecoveryExportInput seed(WebApplicationContext context) throws Exception {
        var fixture = new TemporaryConfirmationFixture(context, false);
        var receipt = SubmissionJson.parse(fixture.execute(
            fixture.command("NORMAL_CONFIRM", fixture.operator()), fixture.operator()));
        return new RecoveryExportInput(fixture.operator().getUsername(),
            fixture.source.f.web.identity().warehouse().toString(),
            receipt.at("/originalContext/canonicalOrderId").asText());
    }

    public String exportPath() { return "/orders/" + order + "/exports?warehouseId=" + warehouse; }
}
