package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Verifies immutable original header commands and the complete version chain before compensation or export. */
@Component
public final class CorrectionHeaderEvidence {
    private final JdbcTemplate jdbc;
    private final CorrectionReadReceipt verifier;
    private final CorrectionHeaderOrigin origin;
    CorrectionHeaderEvidence(JdbcTemplate jdbc, CorrectionReadReceipt verifier, CorrectionHeaderOrigin origin) {
        this.jdbc = jdbc; this.verifier = verifier; this.origin = origin;
    }

    JsonNode normalize(UUID order, JsonNode snapshot) { return origin.normalize(order, snapshot); }

    JsonNode createPatch(CorrectionCommand.Write write) {
        var create = original(write.orderId(), write.documentId(), "CREATE");
        var body = create.command(); var decision = create.receipt().path("businessDecision");
        require("HEADER_CORRECTION".equals(body.path("type").asText())
            && write.anchor().path("documentSha256").equals(create.receipt().path("commandSha256"))
            && write.anchor().path("requestedBy").equals(create.receipt().path("actor").path("principalId"))
            && write.anchor().path("expectedResultVersion").equals(body.path("expected").path("currentResultVersion"))
            && write.documentId().toString().equals(decision.path("documentId").asText()));
        return body.path("headerPatch");
    }

    public Compensation compensate(UUID order, UUID document, long previousVersion) {
        try { return checkedCompensation(order, document, previousVersion); }
        catch (com.acrylic.outbound.common.BusinessException failure) { throw failure; }
        catch (RuntimeException damaged) { throw CorrectionCommand.corrupt(); }
    }

    private Compensation checkedCompensation(UUID order, UUID document, long previousVersion) {
        var proof = approval(order, document);
        var history = versions(order, proof.effectiveVersion(), previousVersion);
        var restored = CorrectionHeaderCompensation.restore(proof.before(), proof.applied(), history);
        var previous = history.getLast();
        var reference = CorrectionHeaderCompensation.proof(new CorrectionHeaderCompensation.ProofInput(
            proof.createReceipt(), proof.approveReceipt(), proof.originalVersion(), proof.effectiveVersion(),
            previousVersion, proof.before(), proof.applied(), previous, restored));
        return new Compensation(SubmissionJson.canonical(restored), reference);
    }

    public void verifyApproval(UUID order, UUID document, long resultVersion) {
        require(approval(order, document).effectiveVersion() == resultVersion);
    }

    public JsonNode withdrawal(UUID order, UUID document, long resultVersion) {
        var compensation = compensate(order, document, resultVersion - 1);
        var receipt = original(order, document, "WITHDRAW_CONFIRM");
        var decision = receipt.receipt().path("businessDecision");
        var documentRow = jdbc.queryForMap("SELECT BIN_TO_UUID(withdrawal_confirmed_by) confirmer,status"
            + " FROM correction_document WHERE id=? AND order_id=?", Ids.bytes(document), Ids.bytes(order));
        require("WITHDRAWN".equals(documentRow.get("status"))
            && documentRow.get("confirmer").equals(receipt.receipt().path("actor").path("principalId").asText())
            && "WITHDRAWN".equals(decision.path("status").asText())
            && decision.path("resultVersion").asLong() == resultVersion
            && decision.path("headerRestoration").equals(compensation.reference())
            && versions(order, resultVersion, resultVersion).getFirst().equals(SubmissionJson.parse(compensation.header())));
        return receipt.receipt();
    }

    private Approval approval(UUID order, UUID document) {
        var row = jdbc.queryForMap("SELECT expected_result_version original,effective_result_version effective,"
            + "document_sha256 digest,BIN_TO_UUID(requested_by) requester,BIN_TO_UUID(approved_by) approver"
            + " FROM correction_document WHERE id=? AND order_id=? AND type='HEADER_CORRECTION'",
            Ids.bytes(document), Ids.bytes(order));
        long original = ((Number) row.get("original")).longValue(), effective = ((Number) row.get("effective")).longValue();
        require(effective == original + 1);
        require(jdbc.queryForObject("SELECT COUNT(*) FROM correction_line WHERE document_id=?", Integer.class, Ids.bytes(document)) == 0);
        var create = original(order, document, "CREATE"); var approve = original(order, document, "APPROVE");
        var decision = approve.receipt().path("businessDecision"); var body = create.command();
        require(row.get("digest").equals(create.receipt().path("commandSha256").asText())
            && row.get("requester").equals(create.receipt().path("actor").path("principalId").asText())
            && row.get("approver").equals(approve.receipt().path("actor").path("principalId").asText())
            && !row.get("requester").equals(row.get("approver"))
            && "HEADER_CORRECTION".equals(body.path("type").asText())
            && original == body.path("expected").path("currentResultVersion").asLong()
            && effective == decision.path("resultVersion").asLong()
            && original == decision.path("previousResultVersion").asLong()
            && "EFFECTIVE".equals(decision.path("status").asText())
            && row.get("digest").equals(decision.path("documentSha256").asText()));
        return snapshots(order, document, original, effective, create, approve);
    }

    private Approval snapshots(UUID order, UUID document, long original, long effective,
        CorrectionReadReceipt.Verified create, CorrectionReadReceipt.Verified approve) {
        var headers = versions(order, original, effective); var before = headers.getFirst(); var applied = headers.getLast();
        require(SubmissionJson.parse(CorrectionHeader.merge(SubmissionJson.canonical(before), create.command().path("headerPatch"))).equals(applied));
        var row = jdbc.queryForMap("SELECT BIN_TO_UUID(r.source_correction_id) document,"
            + "r.actual_count=p.actual_count AND r.weight_g=p.weight_g AND r.inventory_deducted=p.inventory_deducted same_totals"
            + " FROM order_result_version r JOIN order_result_version p ON p.order_id=r.order_id AND p.result_version=r.result_version-1"
            + " WHERE r.order_id=? AND r.result_version=?", Ids.bytes(order), effective);
        require(document.toString().equals(row.get("document")) && CorrectionResultSnapshots.flag(row.get("same_totals")));
        var hashes = approve.receipt().path("businessDecision").path("headerProof");
        if (!hashes.isMissingNode()) require(hashes.equals(approvalHashes(before, applied)));
        return new Approval(original, effective, before, applied, create.receipt().path("receiptId").asText(), approve.receipt().path("receiptId").asText());
    }

    private List<JsonNode> versions(UUID order, long first, long last) {
        require(first > 0 && last >= first);
        var rows = jdbc.queryForList("SELECT result_version,CAST(header_snapshot AS CHAR) header FROM order_result_version"
            + " WHERE order_id=? AND result_version BETWEEN ? AND ? ORDER BY result_version", Ids.bytes(order), first, last);
        require(rows.size() == last - first + 1);
        for (int index = 0; index < rows.size(); index++)
            require(((Number) rows.get(index).get("result_version")).longValue() == first + index);
        var base = origin.base(order);
        return rows.stream().map(row -> CorrectionHeaderOrigin.fill(SubmissionJson.parse(row.get("header").toString()), base)).map(node -> (JsonNode) node).toList();
    }

    private CorrectionReadReceipt.Verified original(UUID order, UUID document, String kind) {
        var rows = jdbc.queryForList("SELECT * FROM correction_command_receipt WHERE order_id=? AND document_id=? AND command_type=?",
            Ids.bytes(order), Ids.bytes(document), kind);
        require(rows.size() == 1); var verified = verifier.verify(rows.getFirst());
        require(order.toString().equals(verified.receipt().path("orderId").asText())
            && document.toString().equals(verified.receipt().path("documentId").asText()));
        return verified;
    }

    static ObjectNode approvalHashes(JsonNode before, JsonNode applied) {
        return SubmissionJson.object().put("beforeHeaderSha256", CorrectionHeaderCompensation.hash(before))
            .put("afterHeaderSha256", CorrectionHeaderCompensation.hash(applied));
    }

    private static void require(boolean condition) { if (!condition) throw CorrectionCommand.corrupt(); }
    public record Compensation(String header, JsonNode reference) { }
    private record Approval(long originalVersion, long effectiveVersion, JsonNode before, JsonNode applied,
        String createReceipt, String approveReceipt) { }
}
