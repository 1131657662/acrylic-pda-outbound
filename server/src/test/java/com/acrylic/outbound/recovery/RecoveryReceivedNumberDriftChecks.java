package com.acrylic.outbound.recovery;

import com.acrylic.outbound.auth.IdentityStore;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.handover.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.Files;
import java.util.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** A number in another warehouse cannot escape the global comparison even when the counter stays unchanged. */
final class RecoveryReceivedNumberDriftChecks {
    static void verify(WebApplicationContext app,RecoveryReceivedArchiveSetup.Context context,RecoveryReleaseInventory inventory,
            RecoveryReleaseNumberHistory.Request request) throws Exception {
        var authority=RecoveryClosedTestSupply.of(context.source()).authority(); var jdbc=context.jdbc();
        var other=jdbc.queryForList("SELECT id,order_no FROM outbound_order WHERE warehouse_id<>?",Ids.bytes(context.source().identity.warehouseId()));
        assertThat(other).hasSize(1); var row=other.getFirst();
        jdbc.update("UPDATE outbound_order SET order_no=? WHERE id=?","FH209912310999",row.get("id"));
        try {
            var observed=inspect(app,context);
            assertThat(observed.facts().path("tables").path("order_number_counter")).isEqualTo(inventory.facts().path("tables").path("order_number_counter"));
            assertThatThrownBy(()->RecoveryReleaseNumberHistory.verify(authority,observed,request)).hasMessage("RECOVERY_NUMBER_TARGET_ORDERS_CHANGED");
        } finally { jdbc.update("UPDATE outbound_order SET order_no=? WHERE id=?",row.get("order_no"),row.get("id")); }
        inventory.requireSame(inspect(app,context));
    }

    private static RecoveryReleaseInventory inspect(WebApplicationContext app,RecoveryReceivedArchiveSetup.Context context) throws Exception {
        byte[] fault=Files.readAllBytes(context.attempt().resolve("recovered-fault/fault-command.json"));
        var receipt=SubmissionJson.parse(Files.readAllBytes(context.attempt().resolve("recovered-fault/fault-receipt.json")));
        var principal=UUID.fromString(receipt.path("actor").path("principalId").asText());
        String username=context.jdbc().queryForObject("SELECT username FROM app_user WHERE id=?",String.class,Ids.bytes(principal));
        var actor=app.getBean(IdentityStore.class).loadUserByUsername(username);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,List.of()));
        try { return app.getBean(HandoverRecoveredTemporaryReview.class).inspect(fault,
            new HandoverRecoveredTemporaryBinding(context.consumer(),context.request(),context.nativeFacts())); }
        finally { SecurityContextHolder.clearContext(); }
    }
    private RecoveryReceivedNumberDriftChecks() { }
}
