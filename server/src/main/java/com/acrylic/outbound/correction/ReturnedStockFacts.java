package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Binds the real cycle reopening to its immutable, independently approved physical correction. */
@Component
final class ReturnedStockFacts {
    private final JdbcTemplate jdbc;
    private final CorrectionReadReceipt verifier;
    ReturnedStockFacts(JdbcTemplate jdbc, CorrectionReadReceipt verifier) { this.jdbc = jdbc; this.verifier = verifier; }

    JsonNode read(UUID dispatch) {
        var rows = jdbc.queryForList("""
            SELECT JSON_OBJECT('reopeningId',BIN_TO_UUID(r.id),'documentId',BIN_TO_UUID(d.id),
              'lineId',BIN_TO_UUID(l.id),'cycleBefore',CAST(r.cycle_before AS CHAR),'cycleAfter',CAST(r.cycle_after AS CHAR),
              'stateVersion',CAST(r.state_version AS CHAR),'evidenceSha256',r.evidence_sha256,
              'documentStatus',d.status,'type',d.type,'documentSha256',d.document_sha256,
              'lineNo',l.line_no,'lineEvidenceSha256',l.evidence_sha256) data
            FROM cycle_reopening r JOIN correction_line l ON l.id=r.correction_line_id
              JOIN correction_document d ON d.id=l.document_id WHERE r.original_dispatch_id=?
            """, Ids.bytes(dispatch));
        if (rows.size()!=1) throw CorrectionCommand.ledgerConflict();
        var proof = (com.fasterxml.jackson.databind.node.ObjectNode) SubmissionJson.parse(rows.getFirst().get("data").toString());
        var receipt = original(proof, "APPROVE").receipt();
        if (!receipt.at("/businessDecision/resultVersion").equals(proof.path("stateVersion"))
            || !receipt.at("/businessDecision/type").equals(proof.path("type"))
            || !receipt.at("/businessDecision/documentSha256").equals(proof.path("documentSha256"))) throw CorrectionCommand.corrupt();
        boolean matches = false;
        for (var item : receipt.at("/businessDecision/reopenedCycles"))
            if (dispatch.toString().equals(item.path("dispatchId").asText())
                && item.path("cycleBefore").equals(proof.path("cycleBefore")) && item.path("cycleAfter").equals(proof.path("cycleAfter"))) matches = true;
        if (!matches) throw CorrectionCommand.corrupt();
        verifyLine(proof, dispatch, receipt);
        proof.set("approvalReceipt", receipt); return proof;
    }

    private CorrectionReadReceipt.Verified original(JsonNode proof, String kind) {
        var rows = jdbc.queryForList("SELECT * FROM correction_command_receipt WHERE document_id=UUID_TO_BIN(?)"
            + " AND command_type=?", proof.path("documentId").asText(), kind);
        if (rows.size()!=1) throw CorrectionCommand.corrupt();
        return verifier.verify(rows.getFirst());
    }

    private void verifyLine(JsonNode proof, UUID dispatch, JsonNode approval) {
        var create = original(proof, "CREATE");
        if (!create.receipt().path("commandSha256").equals(proof.path("documentSha256"))
            || !create.command().path("type").equals(proof.path("type"))
            || !proof.path("evidenceSha256").equals(proof.path("lineEvidenceSha256"))) throw CorrectionCommand.corrupt();
        int index = proof.path("lineNo").asInt()-1;
        var frozen = create.receipt().at("/businessDecision/lines").path(index);
        var requested = create.command().path("lines").path(index);
        if (!frozen.path("lineId").equals(proof.path("lineId")) || !dispatch.toString().equals(frozen.path("dispatchId").asText())
            || !requested.path("evidenceSha256").equals(proof.path("evidenceSha256"))) throw CorrectionCommand.corrupt();
        boolean matched = false;
        for (var effect : approval.at("/businessDecision/effects"))
            if (effect.path("lineId").equals(proof.path("lineId")) && effect.path("dispatchId").equals(frozen.path("dispatchId"))
                && effect.path("countDelta").equals(frozen.path("countDelta"))
                && effect.path("weightDeltaG").equals(frozen.path("weightDeltaG"))) matched = true;
        if (!matched) throw CorrectionCommand.corrupt();
    }
}
