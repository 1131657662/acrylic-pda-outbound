package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;
import static com.acrylic.outbound.inventory.TemporaryConfirmationChecks.reject;

final class TemporaryConfirmationEligibilityChecks {
    private final WebApplicationContext context;
    TemporaryConfirmationEligibilityChecks(WebApplicationContext context) { this.context=context; }
    void verify() throws Exception { currentSource();lateConfirmation();zeroProduct(); }
    private void currentSource() throws Exception {
        var f=new TemporaryConfirmationFixture(context,false);var actor=f.supervisor();var command=f.command("NORMAL_CONFIRM",actor);
        reject("SHORT_SHIPMENT_NOT_PRESENT",()->f.execute(f.command("SHORT_APPROVE",actor),actor));
        String device=f.source.a.device();
        f.jdbc.update("UPDATE device SET temporary_order_enabled=false WHERE id=UUID_TO_BIN(?)",device);
        unchanged(f,"TEMPORARY_ADMISSION_DISABLED",()->f.execute(command,actor));
        f.jdbc.update("UPDATE device SET temporary_order_enabled=true WHERE id=UUID_TO_BIN(?)",device);
        f.jdbc.update("UPDATE order_submission_slot SET slot_version=slot_version+1 WHERE current_submission_id=UUID_TO_BIN(?)",f.source.id());
        unchanged(f,"COMMAND_CONTEXT_CHANGED",()->f.execute(command,actor));
        f.only(f.operator(),List.of("pda:scan","pda:submit"));
        unchanged(f,"TEMPORARY_OPERATOR_PERMISSION_CHANGED",()->f.execute(f.command("NORMAL_CONFIRM",actor),actor));
    }
    private void lateConfirmation() throws Exception {
        var f=new TemporaryConfirmationFixture(context,false);var actor=f.operator();
        // Inject only the eligibility clock. Original 48-hour authorization, signatures and saved times stay intact.
        var store=f.bean(com.acrylic.outbound.temporary.TemporaryStore.class);
        var original=store.get(SubmissionJson.uuid(f.source.body,"authorizationId"));
        var eligibility=new com.acrylic.outbound.temporary.review.TemporaryReviewEligibility(f.jdbc,store,f.bean(com.acrylic.outbound.auth.IdentityStore.class)) {
            @Override public com.fasterxml.jackson.databind.node.ArrayNode current(com.acrylic.outbound.temporary.TemporaryStore.Stored value,com.acrylic.outbound.common.ServerState.State state) {
                return super.current(value,state,original.expiresAt().plusSeconds(1));
            }
        };
        var source=new com.acrylic.outbound.temporary.command.TemporaryConfirmationSource(f.bean(com.acrylic.outbound.submissions.SubmissionStore.class),
            f.bean(com.acrylic.outbound.submissions.SubmissionFiles.class),f.bean(com.acrylic.outbound.temporary.TemporarySubmissionPlan.class),
            f.bean(com.acrylic.outbound.temporary.TemporarySubmissionEvidence.class),f.bean(com.acrylic.outbound.temporary.TemporarySubmissionSnapshot.class),eligibility,f.bean(com.acrylic.outbound.common.ServerState.class),f.jdbc,
            f.bean(com.acrylic.outbound.temporary.continuation.TemporaryRoundConfirmationSource.class),
            f.bean(com.acrylic.outbound.handover.TemporaryHandoverConfirmationSource.class));
        var service=f.service(new TemporaryConfirmationFixture.Assembly(f.bean(com.acrylic.outbound.temporary.command.CommandReceiptCodec.class),
            f.bean(com.acrylic.outbound.audit.AuditWriter.class),f.bean(com.acrylic.outbound.temporary.command.CommandLocks.class),source,f.bean(com.acrylic.outbound.submissions.SubmissionAccounting.class)));
        assertThat(SubmissionJson.parse(f.execute(service,f.command("NORMAL_CONFIRM",actor),actor)).at("/businessDecision/submissionReceipt/status").asText()).isEqualTo("SUCCEEDED");
    }
    private void zeroProduct() throws Exception {
        var f=new TemporaryConfirmationFixture(context,true,TemporaryConfirmationEligibilityChecks::voidSecondProduct);
        var supervisor=f.supervisor();f.execute(f.command("SHORT_APPROVE",supervisor),supervisor);
        var receipt=SubmissionJson.parse(f.execute(f.command("NORMAL_CONFIRM",f.operator()),f.operator()));
        assertThat(receipt.at("/businessDecision/products/1/actualCount").asText()).isEqualTo("0");
        assertThat(receipt.at("/businessDecision/submissionReceipt/actualCount").asText()).isEqualTo("1");
    }
    private static void voidSecondProduct(TemporarySubmissionFixture f) {
        var body=f.body;var scan=(ObjectNode)body.withArray("scans").get(1);scan.put("status","VOID");
        var events=body.withArray("events");var frozen=(ObjectNode)events.remove(events.size()-1);
        events.add(frozen.deepCopy().put("id",java.util.UUID.randomUUID().toString()).put("localSeq","7")
            .put("type","VOID").put("captureId",scan.path("captureId").asText()).put("detail","卸回核对"));
        events.add(frozen.put("localSeq","8"));
        body.put("expectedCount","1").put("expectedWeightGrams",body.at("/scans/0/weightGrams").asText());
        ((ObjectNode)body.path("captureSummary")).put("saved","1").put("voided","1");
    }
    private void unchanged(TemporaryConfirmationFixture f,String code,org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        var before=f.state();reject(code,action);assertThat(f.state()).isEqualTo(before);
    }
}
