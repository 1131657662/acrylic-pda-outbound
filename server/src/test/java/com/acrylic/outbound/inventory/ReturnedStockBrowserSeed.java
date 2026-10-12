package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import org.springframework.web.context.WebApplicationContext;

/** Preserved physical-return facts for separate Chrome sessions; no browser reconciliation decision is prefilled. */
final class ReturnedStockBrowserSeed {
    static void prepare(WebApplicationContext context,Path attempt) throws Exception {
        var f = new ReturnedStockHttpFixture(context,attempt,"browser");
        String checker = f.original.jdbc.queryForObject("SELECT username FROM app_user u JOIN user_role ur ON ur.user_id=u.id"
            + " JOIN role r ON r.id=ur.role_id JOIN user_warehouse uw ON uw.user_id=u.id"
            + " WHERE r.code='SUPERVISOR' AND uw.warehouse_id=UUID_TO_BIN(?) ORDER BY u.id DESC LIMIT 1",String.class,f.original.warehouse);
        var input = SubmissionJson.object().put("warehouse",f.original.warehouse).put("order",f.original.order).put("dispatch",f.original.dispatch);
        input.putObject("maker").put("username",f.original.makerIdentity.user().username()).put("password","Test-password-123");
        input.putObject("checker").put("username",checker).put("password","Test-password-123");
        Files.writeString(f.original.evidence.resolve("browser-input.json"),input.toPrettyString());
    }
    private ReturnedStockBrowserSeed() { }
}
