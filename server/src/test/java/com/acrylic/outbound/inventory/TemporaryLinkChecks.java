package com.acrylic.outbound.inventory;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.auth.SessionUser;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.command.*;
import com.acrylic.outbound.temporary.review.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/**
 * Isolated MySQL verification of the R06 M2b-2b controlled-linkage manual confirmation gate.
 * Positive cases are author self-review; the discriminating error paths exist to be falsifiable and
 * are re-run with the confirmation binding byte-removed outside this class (see the acceptance record).
 */
public final class TemporaryLinkChecks {
    private final WebApplicationContext context;
    public TemporaryLinkChecks(WebApplicationContext context) { this.context=context; }

    public void verify() throws Exception {
        // No production operation is registered, so no code path can link anything automatically.
        assertThat(context.getBeansOfType(CommandOperation.class)).isEmpty();
        linked();
        staleContextAfterLink();
        manualConfirmationSkipped();
        forgedBinding();
        duplicatesUnresolved();
        noCandidate();
        targetChangedAfterReview();
        planChangedAfterReview();
        shortShipmentUnresolved();
    }

    private void linked() throws Exception {
        var s=new Scenario();var target=s.webOrder("FH-LINK-0001");
        assertThat(s.candidateTotal()).isEqualTo(1);
        var command=s.command();byte[] original=s.execute(command);
        var receipt=SubmissionJson.parse(original);
        assertThat(receipt.path("status").asText()).isEqualTo("RECORDED");
        assertThat(receipt.path("commandType").asText()).isEqualTo("LINK");
        assertThat(receipt.at("/businessDecision/kind").asText()).isEqualTo("LINK");
        assertThat(receipt.at("/businessDecision/candidates/total").asText()).isEqualTo("1");
        assertThat(receipt.at("/businessDecision/submissionReceipt/applyState").asText()).isEqualTo("APPLIED");
        assertThat(receipt.at("/businessDecision/submissionReceipt/targetOrderId").asText()).isEqualTo(target);
        assertThat(receipt.at("/businessDecision/submissionReceipt/sourceCanonicalOrderId").asText()).isEqualTo(s.tempOrder);
        assertThat(receipt.at("/businessDecision/submissionReceipt/mappingVersion").asText()).isEqualTo("2");
        assertThat(receipt.at("/businessDecision/submissionReceipt/mappingId").asText()).isEqualTo(s.mappingId(2));
        assertThat(s.execute(command)).isEqualTo(original);
        var view=s.http("/pda/temporary-command-receipts/"+command.path("commandId").asText()+"?warehouseId="+s.warehouse);
        assertThat(view.path("originalCommandReceiptUtf8").asText().getBytes(StandardCharsets.UTF_8)).isEqualTo(original);
        assertThat(view.at("/currentEffect/evaluation").asText()).as("currentEffect=%s",view.path("currentEffect"))
            .isEqualTo("LINK_IN_FORCE");
        var capabilities=receipt.path("capabilities");
        for(String key:List.of("writeConfirmationAvailable","releaseAllowed","stockReleased","mayResumeScanning","localDeletionAllowed"))
            assertThat(capabilities.path(key).asBoolean()).isFalse();
        // The manual confirmation and its pointer switch share one transaction and are visible together.
        assertThat(s.mappingVersion()).isEqualTo(2);
        assertThat(s.canonical()).isEqualTo(target);
        assertThat(s.slotOrder()).isEqualTo(target);
        assertThat(s.slotVersion()).isEqualTo(2);
        assertThat(s.tempOrderStatus()).isEqualTo("LINKED");
        assertThat(s.targetStatus(target)).isEqualTo("READY");
        assertThat(s.mappingRows()).containsExactly("0:SOURCE_REGISTERED:NULL","1:INITIAL_ACCEPTANCE:"+s.tempOrder,
            "2:CONTROLLED_LINK:"+target);
        assertThat(s.targetWorkRows(target)).isZero();
        assertThat(s.targetScopes(target)).isEqualTo(1);
        assertThat(s.decisionVersion()).isEqualTo(1);
        assertThat(s.receipts()).isEqualTo(1);
        assertThat(s.idempotencyRequests()).isEqualTo(1);
    }

    /** After the switch the old target context is gone: the same source can neither re-link nor reuse the old pointers. */
    private void staleContextAfterLink() throws Exception {
        var s=new Scenario();var target=s.webOrder("FH-LINK-0009");
        var oldContext=s.command();
        s.execute(s.command());
        TemporaryConfirmationChecks.reject("COMMAND_CONTEXT_CHANGED",()->s.execute(oldContext));
        assertThat(s.mappingVersion()).isEqualTo(2);
        assertThat(s.canonical()).isEqualTo(target);
        assertThat(s.mappingRows()).hasSize(3);
        assertThat(s.receipts()).isEqualTo(1);
        assertThat(s.idempotencyRequests()).isEqualTo(1);
        assertThat(s.tempOrderStatus()).isEqualTo("LINKED");
    }

    private void manualConfirmationSkipped() throws Exception {
        var s=new Scenario();var target=s.webOrder("FH-LINK-0002");
        var command=s.command();((ObjectNode)command.get("expected")).putNull("candidateContextSha256");
        TemporaryConfirmationChecks.reject("TEMPORARY_MANUAL_CONFIRMATION_REQUIRED",()->s.execute(command));
        s.unchanged(target);
    }

    private void forgedBinding() throws Exception {
        var s=new Scenario();var target=s.webOrder("FH-LINK-0003");
        var command=s.command();var expected=(ObjectNode)command.get("expected");
        String digest=expected.path("candidateContextSha256").asText();
        expected.put("candidateContextSha256",(digest.startsWith("0")?"1":"0")+digest.substring(1));
        TemporaryConfirmationChecks.reject("COMMAND_CONTEXT_CHANGED",()->s.execute(command));
        s.unchanged(target);
    }

    private void duplicatesUnresolved() throws Exception {
        var s=new Scenario();var first=s.webOrder("FH-LINK-0004");s.webOrder("FH-LINK-0005");
        assertThat(s.candidateTotal()).isEqualTo(2);
        var command=s.command();
        TemporaryConfirmationChecks.reject("TEMPORARY_LINK_DUPLICATES_UNRESOLVED",()->s.execute(command));
        s.unchanged(first);
    }

    private void noCandidate() throws Exception {
        var s=new Scenario();var command=s.command();
        assertThat(s.candidateTotal()).isZero();
        TemporaryConfirmationChecks.reject("TEMPORARY_LINK_TARGET_NOT_FOUND",()->s.execute(command));
        s.unchanged(null);
    }

    private void targetChangedAfterReview() throws Exception {
        var s=new Scenario();var target=s.webOrder("FH-LINK-0006");var command=s.command();
        s.f.jdbc.update("UPDATE outbound_order SET row_version=row_version+1 WHERE id=UUID_TO_BIN(?)",target);
        TemporaryConfirmationChecks.reject("COMMAND_CONTEXT_CHANGED",()->s.execute(command));
        s.unchanged(target);
    }

    private void planChangedAfterReview() throws Exception {
        var s=new Scenario();var target=s.webOrder("FH-LINK-0007");var command=s.command();
        // In-place plan edit under the same revision: the reviewed head digest is unchanged, so only the
        // lock-scoped recheck can refuse it.
        s.f.jdbc.update("UPDATE order_line SET planned_count=planned_count+1 WHERE revision_id=UUID_TO_BIN(?)",s.revisionId(target));
        assertThat(s.digest(command)).isEqualTo(command.at("/expected/candidateContextSha256").asText());
        TemporaryConfirmationChecks.reject("TEMPORARY_LINK_TARGET_PLAN_DIFFERS",()->s.execute(command));
        s.unchanged(target);
    }

    private void shortShipmentUnresolved() throws Exception {
        var s=new Scenario(true);var target=s.webOrder("FH-LINK-0008");
        var command=s.command();
        TemporaryConfirmationChecks.reject("TEMPORARY_LINK_SHORT_SHIPMENT_UNRESOLVED",()->s.execute(command));
        s.unchanged(target);
    }

    private final class Scenario {
        final TemporaryConfirmationFixture f;final SessionUser actor;final TemporaryCommandService service;
        final String warehouse;final String tempOrder;
        Scenario() throws Exception { this(false); }
        Scenario(boolean shortShipment) throws Exception {
            f=new TemporaryConfirmationFixture(context,shortShipment);actor=f.operator();
            warehouse=f.source.f.web.identity().warehouse().toString();grantLink();
            var components=new TemporaryConfirmationFixture.Assembly(f.bean(CommandReceiptCodec.class),f.bean(AuditWriter.class),
                f.bean(CommandLocks.class),f.bean(TemporaryConfirmationSource.class),f.bean(SubmissionAccounting.class));
            service=f.assemble(components,List.of(new TemporaryLinkOperation(new TemporaryLinkOperation.Dependencies(
                f.bean(TemporaryConfirmationSource.class),f.bean(TemporaryDecisionCandidates.class),
                f.bean(TemporaryLinkTarget.class),f.bean(TemporaryLinkMapping.class)))));
            tempOrder=f.jdbc.queryForObject("SELECT BIN_TO_UUID(canonical_order_id) FROM submission_scope WHERE current_submission_id=UUID_TO_BIN(?)",
                String.class,f.source.id());
            // Earlier scenarios reuse the same synthetic vehicle and products; push their orders out of this window.
            f.jdbc.update("UPDATE outbound_order SET created_at=DATE_SUB(created_at,INTERVAL 30 DAY) WHERE source='WEB' AND status='READY' AND warehouse_id=UUID_TO_BIN(?)",warehouse);
        }
        private void grantLink() {
            UUID role=Ids.next();
            f.jdbc.update("INSERT INTO role VALUES(?,?,?)",Ids.bytes(role),"LINK_"+role.toString().substring(20),"隔离关联权限");
            f.jdbc.update("INSERT INTO role_permission SELECT ?,id FROM permission WHERE code='temporary_order:link'",Ids.bytes(role));
            f.jdbc.update("INSERT INTO user_role VALUES(?,?)",Ids.bytes(actor.id()),Ids.bytes(role));
        }
        ObjectNode command() {
            var command=f.command("LINK",actor);command.put("reason","人工核对疑似重复正式单后确认受控关联");return command;
        }
        byte[] execute(ObjectNode command) { return f.execute(service,command,actor); }
        JsonNode http(String path) throws Exception { return f.transport.http(f.source,path,null); }
        String digest(ObjectNode command) {
            return f.bean(TemporaryDecisionCandidates.class)
                .snapshot(UUID.fromString(warehouse),UUID.fromString(f.source.id()),f.source.body).path("contextSha256").asText();
        }
        int candidateTotal() {
            return f.bean(TemporaryDecisionCandidates.class)
                .snapshot(UUID.fromString(warehouse),UUID.fromString(f.source.id()),f.source.body).path("total").asInt();
        }
        String webOrder(String orderNo) {
            UUID order=Ids.next(),revision=Ids.next();
            f.jdbc.update("INSERT INTO outbound_order(id,warehouse_id,order_no,source,status,current_revision,created_by,created_at,updated_at) "
                +"VALUES(?,?,?,'WEB','READY',NULL,?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",Ids.bytes(order),Ids.bytes(UUID.fromString(warehouse)),
                orderNo,Ids.bytes(actor.id()));
            f.jdbc.update("""
                INSERT INTO order_revision(id,order_id,warehouse_id,revision,inventory_snapshot_id,mapping_revision,vehicle_no,container_no,
                 notes,planned_total,reason,created_by,created_at)
                SELECT ?,?,?,1,inventory_snapshot_id,mapping_revision,vehicle_no,container_no,notes,planned_total,reason,created_by,created_at
                FROM order_revision WHERE order_id=UUID_TO_BIN(?) AND revision=1
                """,Ids.bytes(revision),Ids.bytes(order),Ids.bytes(UUID.fromString(warehouse)),tempOrder);
            f.jdbc.update("INSERT INTO order_line(revision_id,delivery_code,mapping_id,product_id,model_snapshot,name_snapshot,planned_count) "
                +"SELECT ?,delivery_code,mapping_id,product_id,model_snapshot,name_snapshot,planned_count FROM order_line "
                +"WHERE revision_id=(SELECT id FROM order_revision WHERE order_id=UUID_TO_BIN(?) AND revision=1)",Ids.bytes(revision),tempOrder);
            f.jdbc.update("UPDATE outbound_order SET current_revision=1 WHERE id=?",Ids.bytes(order));
            return order.toString();
        }
        String revisionId(String order) {
            return f.jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM order_revision WHERE order_id=UUID_TO_BIN(?) AND revision=1",String.class,order);
        }
        long mappingVersion() { return query("SELECT mapping_version FROM submission_scope WHERE current_submission_id=UUID_TO_BIN(?)"); }
        String mappingId(long version) {
            return f.jdbc.queryForObject("SELECT BIN_TO_UUID(m.id) FROM temporary_source_mapping m JOIN submission s ON s.scope_id=m.scope_id "
                +"WHERE s.id=UUID_TO_BIN(?) AND m.mapping_version=?",String.class,f.source.id(),version);
        }
        List<String> mappingRows() {
            return f.jdbc.queryForList("SELECT CONCAT(m.mapping_version,':',m.origin,':',IFNULL(BIN_TO_UUID(m.canonical_order_id),'NULL')) "
                +"FROM temporary_source_mapping m JOIN submission s ON s.scope_id=m.scope_id WHERE s.id=UUID_TO_BIN(?) ORDER BY m.mapping_version",
                String.class,f.source.id());
        }
        String scopeId() {
            return f.jdbc.queryForObject("SELECT BIN_TO_UUID(scope_id) FROM submission WHERE id=UUID_TO_BIN(?)",String.class,f.source.id());
        }
        String slotOrder() {
            return f.jdbc.queryForObject("SELECT BIN_TO_UUID(order_id) FROM order_submission_slot WHERE scope_id=UUID_TO_BIN(?)",String.class,scopeId());
        }
        long slotVersion() {
            return f.jdbc.queryForObject("SELECT slot_version FROM order_submission_slot WHERE scope_id=UUID_TO_BIN(?)",Long.class,scopeId());
        }
        String targetStatus(String order) {
            return f.jdbc.queryForObject("SELECT status FROM outbound_order WHERE id=UUID_TO_BIN(?)",String.class,order);
        }
        int targetScopes(String order) {
            return f.jdbc.queryForObject("SELECT COUNT(*) FROM submission_scope WHERE canonical_order_id=UUID_TO_BIN(?)",Integer.class,order);
        }
        int targetWorkRows(String order) {
            return f.jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM order_assignment WHERE order_id=o.id)"
                +"+(SELECT COUNT(*) FROM pc_work WHERE order_id=o.id)"
                +"+(SELECT COUNT(*) FROM order_result_version WHERE order_id=o.id) FROM outbound_order o WHERE o.id=UUID_TO_BIN(?)",
                Integer.class,order);
        }
        String canonical() {
            return f.jdbc.queryForObject("SELECT BIN_TO_UUID(canonical_order_id) FROM submission_scope WHERE current_submission_id=UUID_TO_BIN(?)",
                String.class,f.source.id());
        }
        long decisionVersion() {
            return f.jdbc.queryForObject("SELECT d.decision_version FROM temporary_decision_state d JOIN submission s ON s.scope_id=d.scope_id "
                +"WHERE s.id=UUID_TO_BIN(?)",Long.class,f.source.id());
        }
        int receipts() { return f.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_command_receipt WHERE submission_id=UUID_TO_BIN(?)",Integer.class,f.source.id()); }
        int idempotencyRequests() {
            return f.jdbc.queryForObject("SELECT COUNT(*) FROM api_idempotency_request q JOIN temporary_command_receipt r ON r.api_request_id=q.id "
                +"WHERE r.submission_id=UUID_TO_BIN(?)",Integer.class,f.source.id());
        }
        String tempOrderStatus() { return f.jdbc.queryForObject("SELECT status FROM outbound_order WHERE id=UUID_TO_BIN(?)",String.class,tempOrder); }
        String submissionStatus() { return f.jdbc.queryForObject("SELECT status FROM submission WHERE id=UUID_TO_BIN(?)",String.class,f.source.id()); }
        private long query(String sql) { return f.jdbc.queryForObject(sql,Long.class,f.source.id()); }
        void unchanged(String target) {
            assertThat(receipts()).isZero();
            assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM api_idempotency_request WHERE resource_id IN "
                +"(SELECT id FROM temporary_command_receipt WHERE submission_id=UUID_TO_BIN(?))",Integer.class,f.source.id())).isZero();
            assertThat(decisionVersion()).isZero();
            assertThat(mappingVersion()).isEqualTo(1);
            assertThat(canonical()).isEqualTo(tempOrder);
            assertThat(tempOrderStatus()).isEqualTo("REVIEW_REQUIRED");
            assertThat(submissionStatus()).isEqualTo("REVIEW_REQUIRED");
            if(target!=null)assertThat(f.jdbc.queryForObject("SELECT status FROM outbound_order WHERE id=UUID_TO_BIN(?)",String.class,target)).isEqualTo("READY");
        }
    }
}
