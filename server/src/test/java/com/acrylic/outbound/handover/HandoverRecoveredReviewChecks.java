package com.acrylic.outbound.handover;

import com.acrylic.outbound.auth.SessionUser;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.recovery.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

/** Actual read-only range capture, with immutable original facts and no release effects. */
public final class HandoverRecoveredReviewChecks {
    public static RecoveryReleaseInventory verify(WebApplicationContext app,Path attempt,HandoverRecoveredTemporaryChecks.Bindings bindings) throws Exception {
        var f=new HandoverRecoveredTemporaryFixture(app,attempt,bindings.original());
        var request=f.request(); byte[] requested=f.execute(f.requester,request);
        var fault=f.fault(uuid(parse(requested).path("businessDecision"),"caseId"));
        byte[] fenced=f.execute(f.supervisor,fault);
        Files.write(f.directory.resolve("request-command.json"),HandoverReceiptCodec.bytes(request));
        Files.write(f.directory.resolve("request-receipt.json"),requested);
        Files.write(f.directory.resolve("fault-command.json"),HandoverReceiptCodec.bytes(fault));
        Files.write(f.directory.resolve("fault-receipt.json"),fenced);
        var review=app.getBean(HandoverRecoveredTemporaryReview.class); var before=f.snapshot("review-before");
        var observed=inspect(review,f.supervisor,fault,bindings.original());
        observed.requireSame(inspect(review,f.supervisor,fault,bindings.original()));
        assertThat(f.snapshot("review-after")).isEqualTo(before);
        assertThat(observed.releaseAllowed()).isFalse(); assertThat(observed.localDeletionAllowed()).isFalse();
        assertThat(observed.gaps()).contains("GLOBAL_NUMBER_HISTORY_REQUIRED","DEVICE_SCOPE_CLOSURE_REQUIRED");
        assertThat(observed.facts().path("tables").path("order_number_counter").path("rows")).isNotEmpty();
        scopeAndCredentials(observed);
        HandoverRecoveredReviewCoverageChecks.verify(observed);
        refusals(review,f,fault,bindings.foreign());
        changedInventory(review,f,fault,observed);
        HandoverRecoveredReviewFenceChecks.verify(review,f,fault);
        HandoverRecoveredReviewTailChecks.verify(app,f,fault);
        assertThat(f.snapshot("review-final")).isEqualTo(before);
        Files.writeString(f.directory.resolve("release-inventory.json"),canonical(observed.facts())+"\n");
        Files.writeString(f.directory.resolve("release-inventory-result.json"),object().put("sha256",observed.sha256())
            .put("readOnly",true).put("releaseAllowed",false).put("ordinaryWritesAllowed",false)
            .put("localDeletionAllowed",false).set("gaps",new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(observed.gaps())).toPrettyString());
        return observed;
    }

    private static void scopeAndCredentials(RecoveryReleaseInventory observed) {
        var tables=observed.facts().path("tables");
        assertThat(tables.path("device").path("rows")).hasSize(1);
        assertThat(tables.path("submission_scope").path("rows")).hasSize(1);
        for(String table:List.of("pda_session","temporary_authorization","offline_authorization")) {
            var data=tables.path(table); var columns=data.path("columns");
            for(int index=0;index<columns.size();index++) {
                if(!List.of("access_hash","refresh_hash","signed_token").contains(columns.get(index).asText())) continue;
                for(JsonNode row:data.path("rows")) assertThat(row.get(index).asText()).startsWith("sha256:");
            }
        }
        for(String table:List.of("background_job","recovery_package")) {
            assertThat(tables.path(table).has("rows")).isFalse();
            assertThat(tables.path(table).path("rowsSha256").asText()).matches("[a-f0-9]{64}");
        }
    }

    static RecoveryReleaseInventory inspect(HandoverRecoveredTemporaryReview review,SessionUser actor,
            JsonNode fault,HandoverRecoveredTemporaryBinding binding) {
        HandoverRecoveredTemporaryFixture.authenticate(actor);
        try { return review.inspect(HandoverReceiptCodec.bytes(fault),binding); }
        finally { SecurityContextHolder.clearContext(); }
    }

    private static void refusals(HandoverRecoveredTemporaryReview review,HandoverRecoveredTemporaryFixture f,
            JsonNode fault,HandoverRecoveredTemporaryBinding foreign) throws Exception {
        var before=f.snapshots.capture();
        assertThatThrownBy(()->inspect(review,f.unprivileged,fault,f.binding))
            .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo("PERMISSION_DENIED"));
        assertThatThrownBy(()->inspect(review,f.supervisor,fault,foreign)).hasMessage("RECOVERY_RELEASE_TRANSACTION_REQUIRED");
        var wrong=((com.fasterxml.jackson.databind.node.ObjectNode)fault).deepCopy().put("caseId",UUID.randomUUID().toString());
        assertThatThrownBy(()->inspect(review,f.supervisor,wrong,f.binding)).isInstanceOf(BusinessException.class);
        var stale=((com.fasterxml.jackson.databind.node.ObjectNode)fault).deepCopy().put("transportRecoveryEpoch","1");
        assertThatThrownBy(()->inspect(review,f.supervisor,stale,f.binding)).isInstanceOf(BusinessException.class);
        assertThat(f.snapshots.capture()).isEqualTo(before);
    }

    private static void changedInventory(HandoverRecoveredTemporaryReview review,HandoverRecoveredTemporaryFixture f,
            JsonNode fault,RecoveryReleaseInventory observed) {
        f.jdbc.update("UPDATE order_number_counter SET last_sequence=last_sequence+1");
        try {
            var changed=inspect(review,f.supervisor,fault,f.binding);
            assertThatThrownBy(()->observed.requireSame(changed)).hasMessage("RECOVERY_RELEASE_INVENTORY_CHANGED");
        } finally { f.jdbc.update("UPDATE order_number_counter SET last_sequence=last_sequence-1"); }
        observed.requireSame(inspect(review,f.supervisor,fault,f.binding));
    }
}
