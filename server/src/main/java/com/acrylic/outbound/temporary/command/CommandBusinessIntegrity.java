package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Historical decision closure uses immutable original facts, never today's permissions or candidate query. */
@Component
public class CommandBusinessIntegrity {
    private static final String PHYSICAL_DISPOSITION_MIGRATION = "55";
    private final JdbcTemplate jdbc;private final SubmissionFiles files;private final SubmissionArchiveProofs proofs;
    private final TemporaryDecisionCandidates candidates; private final TemporaryContinuationIntegrity continuation;
    public CommandBusinessIntegrity(JdbcTemplate jdbc,SubmissionFiles files,SubmissionArchiveProofs proofs,
        TemporaryDecisionCandidates candidates,TemporaryContinuationIntegrity continuation) {
        this.jdbc=jdbc;this.files=files;this.proofs=proofs;this.candidates=candidates;this.continuation=continuation;
    }
    void verify(JsonNode receipt) {
        if(receipt.path("businessDecision").isNull())return;
        if (Set.of("PHYSICAL_DISPOSITION","CLOSE_DISPOSITION").contains(receipt.path("commandType").asText())) {
            verifyDisposition(receipt);
            return;
        }
        if (Set.of("REOPEN_WORK","ACTIVATE_CONTINUATION").contains(receipt.path("commandType").asText())) { continuation.verify(receipt); return; }
        var context=receipt.path("originalContext");var decision=receipt.path("businessDecision");
        if(context.path("workRound").asLong()>1)CommandErrors.integrity(Set.of("NORMAL_CONFIRM","SHORT_APPROVE","LINK").contains(receipt.path("commandType").asText()));
        String hash=context.path("receivedPayloadSha256").asText();var body=parse(files.read(hash));
        CommandErrors.integrity(sha(body).equals(hash) && context.path("status").asText().equals("REVIEW_REQUIRED")
            && context.path("planSha256").equals(body.path("planSha256"))
            && body.path("submissionId").equals(context.path("submissionId")));
        original(context,body);
        CommandErrors.integrity(TemporaryDecisionCandidates.products(body).equals(decision.path("products"))
            && decision.path("shortShipmentReason").asText().equals(body.path("shortShipmentReason").asText(""))
            && candidates.authentic(uuid(context,"warehouseId"),uuid(context,"submissionId"),body,decision.path("candidates")));
        boolean shortShipment=false;for(var product:decision.path("products"))shortShipment|=product.path("shortCount").asLong()>0;
        if(CommandDecision.kind(receipt).equals("LINK")) { link(context,decision);return; }
        if(CommandDecision.kind(receipt).equals("REJECT")) { rejected(context,body);return; }
        if(CommandDecision.kind(receipt).equals("SHORT_APPROVE")) {
            CommandErrors.integrity(shortShipment && !decision.path("shortShipmentReason").asText().isBlank()
                && !receipt.path("actor").path("principalId").equals(context.path("originalOperatorId")));return;
        }
        CommandErrors.integrity(shortShipment!=decision.path("shortApprovalReceiptId").isNull());
        success(context,decision.path("submissionReceipt"),body);
        if(context.path("workRound").asLong()>1)TemporaryRoundReceiptIntegrity.completed(jdbc,receipt);
    }
    private void verifyDisposition(JsonNode receipt) {
        var context = receipt.path("originalContext");
        var decision = receipt.path("businessDecision");
        boolean close = "CLOSE_DISPOSITION".equals(receipt.path("commandType").asText());
        CommandErrors.integrity(decision.path("evidenceSetId").asText().equals(receipt.path("expected").path("evidenceSetId").asText())
            && decision.path("verificationId").asText().equals(receipt.path("expected").path("verificationId").asText())
            && decision.path("manifestSha256").asText().equals(receipt.path("expected").path("dispositionManifestSha256").asText())
            && decision.path("manifestVersion").asText().equals(receipt.path("expected").path("dispositionManifestVersion").asText()));
        var rows = jdbc.queryForList("SELECT close_mode,close_reason FROM temporary_physical_disposition WHERE id=? AND scope_id=? AND submission_id=? AND work_round=?",
            Ids.bytes(UUID.fromString(decision.path("dispositionId").asText())), Ids.bytes(uuid(context,"scopeId")),
            Ids.bytes(uuid(context,"submissionId")), Long.parseLong(context.path("workRound").asText()));
        CommandErrors.integrity(rows.size()==1 && (close == truth(rows.getFirst().get("close_mode")))
            && (!close || decision.path("closeReason").asText().equals(rows.getFirst().get("close_reason"))));
        verifyDispositionItems(decision, context);
        if (close) verifyClosedSlot(decision, context);
    }
    private void verifyDispositionItems(JsonNode decision, JsonNode context) {
        var expected = new ArrayList<String>();
        for (var item : decision.path("products")) {
            String bale = item.path("baleId").asText();
            String action = item.path("disposition").asText();
            expected.add(bale + ":" + ("PHYSICAL_DISPOSED".equals(action) ? "QUARANTINE" : action));
        }
        expected.sort(String::compareTo);
        var actualRows = jdbc.queryForList("SELECT BIN_TO_UUID(i.bale_id) bale_id,i.action FROM temporary_physical_disposition_item i "
            + "JOIN temporary_physical_disposition d ON d.id=i.disposition_id WHERE d.id=? AND d.scope_id=? ORDER BY i.bale_id",
            Ids.bytes(UUID.fromString(decision.path("dispositionId").asText())), Ids.bytes(uuid(context,"scopeId")));
        var actual = actualRows.stream().map(row -> row.get("bale_id") + ":" + row.get("action")).toList();
        CommandErrors.integrity(expected.equals(actual));
    }
    private void verifyClosedSlot(JsonNode decision, JsonNode context) {
        boolean hasOrder = !context.path("canonicalOrderId").isNull();
        CommandErrors.integrity(decision.path("scopeClosed").asBoolean() && decision.path("slotClosed").asBoolean() == hasOrder);
        if (!hasOrder) return;
        var slots = jdbc.queryForList("SELECT phase,current_submission_id FROM order_submission_slot WHERE scope_id=?",
            Ids.bytes(uuid(context,"scopeId")));
        CommandErrors.integrity(slots.size()==1 && "CLOSED".equals(slots.getFirst().get("phase"))
            && slots.getFirst().get("current_submission_id") == null);
    }
    private void rejected(JsonNode context,JsonNode body) {
        var rows=jdbc.queryForList("SELECT status,issues_json FROM submission WHERE id=?",Ids.bytes(uuid(context,"submissionId")));
        CommandErrors.integrity(rows.size()==1 && "REJECTED".equals(rows.getFirst().get("status")));
        var issues=rows.getFirst().get("issues_json");
        CommandErrors.integrity(issues!=null);
        var stored=parse(issues.toString());var entries=stored.isArray()?stored:stored.path("issues");
        boolean rejected=false;
        for(var issue:entries)rejected|="SUPERVISOR_REJECTED".equals(issue.path("code").asText());
        CommandErrors.integrity(entries.isArray() && rejected);
        var expected=new ArrayList<String>();
        for(var scan:body.path("scans"))if("ACTIVE".equals(scan.path("status").asText()))
            expected.add(scan.path("baleId").asText()+":"+scan.path("expectedDispatchCycle").asText());
        var reserved=rejectionReservations(context);
        var disposed=rejectionDisposition(context);
        CommandErrors.integrity(!expected.isEmpty() && new HashSet<>(expected).size()==expected.size());
        CommandErrors.integrity(validSubset(reserved, expected) && validSubset(disposed, expected));
        // Rejection can retain a subset when another source already owns conflicting bales.
        // A committed physical disposition, however, must account for the complete verified set.
        CommandErrors.integrity(disposed.isEmpty() ? !reserved.isEmpty()
            : reserved.isEmpty() && disposed.size()==expected.size());
    }
    private boolean validSubset(List<String> actual, List<String> expected) {
        return new HashSet<>(actual).size()==actual.size() && expected.containsAll(actual);
    }
    private ArrayList<String> rejectionReservations(JsonNode context) {
        return new ArrayList<>(jdbc.queryForList("""
            SELECT CONCAT(BIN_TO_UUID(bale_id),':',dispatch_cycle) FROM bale_commitment
            WHERE submission_id=? AND source_scope_id=? AND source_warehouse_id=? AND state='REVIEW_RESERVED'
            """,String.class,Ids.bytes(uuid(context,"submissionId")),Ids.bytes(uuid(context,"scopeId")),
            Ids.bytes(uuid(context,"warehouseId"))));
    }
    private List<String> rejectionDisposition(JsonNode context) {
        if (!physicalDispositionMigrationApplied()) return List.of();
        return jdbc.queryForList("""
            SELECT CONCAT(BIN_TO_UUID(i.bale_id),':',i.dispatch_cycle) FROM temporary_physical_disposition_item i
            JOIN temporary_physical_disposition d ON d.id=i.disposition_id
            JOIN source_evidence_verification v ON v.id=d.evidence_verification_id AND v.set_id=d.evidence_set_id
              AND v.scope_id=d.scope_id AND v.submission_id=d.submission_id AND v.work_round=d.work_round
            WHERE d.submission_id=? AND d.scope_id=? AND d.warehouse_id=? AND d.work_round=?
              AND v.result='VERIFIED_COMPLETE_AT_STOP' AND i.disposition='PHYSICAL_DISPOSED'
            """,String.class,Ids.bytes(uuid(context,"submissionId")),Ids.bytes(uuid(context,"scopeId")),
            Ids.bytes(uuid(context,"warehouseId")),Long.parseLong(context.path("workRound").asText()));
    }
    private boolean physicalDispositionMigrationApplied() {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM flyway_schema_history WHERE version=? AND success=1)
            """,Boolean.class,PHYSICAL_DISPOSITION_MIGRATION));
    }
    private void link(JsonNode context,JsonNode decision) {
        var link=decision.path("submissionReceipt");
        CommandErrors.integrity(link.path("candidateContextSha256").equals(decision.path("candidates").path("contextSha256"))
            && link.path("sourceMappingVersion").equals(context.path("mappingVersion"))
            && link.path("sourceCanonicalOrderId").equals(context.path("canonicalOrderId")));
        var target=jdbc.queryForMap("SELECT source FROM outbound_order WHERE id=? AND warehouse_id=?",
            Ids.bytes(UUID.fromString(link.path("targetOrderId").asText())),Ids.bytes(uuid(context,"warehouseId")));
        CommandErrors.integrity("WEB".equals(target.get("source").toString()));
        if(!link.path("applyState").asText().equals("APPLIED"))return;
        var applied=jdbc.queryForMap("SELECT BIN_TO_UUID(scope_id) scope_id,BIN_TO_UUID(warehouse_id) warehouse_id,"
            +"BIN_TO_UUID(canonical_order_id) canonical_order_id,mapping_version,origin FROM temporary_source_mapping WHERE id=?",
            Ids.bytes(UUID.fromString(link.path("mappingId").asText())));
        CommandErrors.integrity("CONTROLLED_LINK".equals(applied.get("origin").toString())
            && applied.get("mapping_version").toString().equals(link.path("mappingVersion").asText())
            && applied.get("canonical_order_id").toString().equals(link.path("targetOrderId").asText())
            && applied.get("scope_id").toString().equals(context.path("scopeId").asText())
            && applied.get("warehouse_id").toString().equals(context.path("warehouseId").asText()));
        var previous=jdbc.queryForList("SELECT BIN_TO_UUID(canonical_order_id) canonical_order_id FROM temporary_source_mapping "
            +"WHERE scope_id=? AND mapping_version=?",Ids.bytes(uuid(context,"scopeId")),Long.parseLong(link.path("sourceMappingVersion").asText()));
        CommandErrors.integrity(previous.size()==1 && Objects.equals(previous.getFirst().get("canonical_order_id"),
            link.path("sourceCanonicalOrderId").isNull()?null:link.path("sourceCanonicalOrderId").asText()));
    }
    private void original(JsonNode context,JsonNode body) {
        for(String field:List.of("authorizationId","freezeDeviceId","workRound","sourceRecoveryEpoch","authorizationEpoch"))
            CommandErrors.integrity(context.path(field).equals(body.path(field)));
        CommandErrors.integrity(context.path("creatorDeviceId").equals(body.path("source").path("creatorDeviceId")));
        if(context.path("workRound").asLong()==1)CommandErrors.integrity(context.path("clientOrderId").equals(body.path("source").path("clientOrderId")));
        else TemporaryRoundReceiptIntegrity.original(jdbc,context,body);
        CommandErrors.integrity(body.path("source").path("kind").asText().equals("TEMP")
            && body.path("assignmentId").isNull() && body.path("continuationId").isNull());
        if(context.path("mappingVersion").asLong()<=1)CommandErrors.integrity(context.path("targetRevision").equals(body.path("orderRevision")));
        else CommandErrors.integrity(jdbc.queryForObject("SELECT COUNT(*) FROM temporary_source_mapping m JOIN order_revision r ON r.order_id=m.canonical_order_id "
            +"WHERE m.scope_id=? AND m.mapping_version=? AND m.origin='CONTROLLED_LINK' AND m.canonical_order_id=? AND r.revision=?",Integer.class,
            Ids.bytes(uuid(context,"scopeId")),context.path("mappingVersion").asLong(),Ids.bytes(uuid(context,"canonicalOrderId")),context.path("targetRevision").asLong())==1);
    }
    private void success(JsonNode context,JsonNode success,JsonNode body) {
        proofs.verify(success,"signedReceipt","acrylic-pda-receipt","receipt");
        for(String field:List.of("submissionId","freezeDeviceId","workRound","captureHighWatermark"))
            CommandErrors.integrity(success.path(field).equals(body.path(field)));
        CommandErrors.integrity(success.path("source").equals(body.path("source")) && success.path("orderId").equals(context.path("canonicalOrderId"))
            && success.path("payloadSha256").equals(context.path("receivedPayloadSha256")) && success.path("status").asText().equals("SUCCEEDED")
            && success.path("actualCount").equals(body.path("expectedCount")) && success.path("actualWeightGrams").equals(body.path("expectedWeightGrams"))
            && success.path("inventoryDeducted").equals(body.path("autoDeductInventory")));
        var row=jdbc.queryForMap("""
            SELECT s.receipt_text,v.header_snapshot,v.actual_count,v.weight_g,v.inventory_deducted
            FROM submission s JOIN order_result_version v ON v.submission_id=s.id AND v.result_version=1
            WHERE s.id=? AND s.status='SUCCEEDED' AND v.order_id=?
            """,Ids.bytes(uuid(context,"submissionId")),Ids.bytes(uuid(context,"canonicalOrderId")));
        CommandErrors.integrity(parse(row.get("receipt_text").toString()).equals(success)
            && parse(row.get("header_snapshot").toString()).equals(success)
            && row.get("actual_count").toString().equals(success.path("actualCount").asText())
            && row.get("weight_g").toString().equals(success.path("actualWeightGrams").asText())
            && truth(row.get("inventory_deducted"))==success.path("inventoryDeducted").asBoolean());
        var items=object().putArray("items");
        for(var product:TemporaryDecisionCandidates.products(body))items.add(object().put("deliveryCode",product.path("deliveryCode").asText())
            .put("actualCount",product.path("actualCount").asText()).put("actualWeightGrams",product.path("weightGrams").asText()));
        CommandErrors.integrity(items.equals(success.path("items")) && success.path("resultVersion").asText().equals("1")
            && success.path("issuedRecoveryEpoch").equals(context.path("sourceRecoveryEpoch")));
    }
    private boolean truth(Object value) { return Boolean.TRUE.equals(value) || value instanceof Number n && n.intValue()==1; }
}
