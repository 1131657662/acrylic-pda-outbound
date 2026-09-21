package com.acrylic.outbound.ledgerverify;

import com.acrylic.outbound.inventory.LedgerVerifySeed;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Inputs use the approved contract and stored original facts, not the ledger's context builder. */
final class LedgerCommandData {
    private final JdbcTemplate jdbc;
    LedgerCommandData(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    ObjectNode command(LedgerVerifySeed seed,String kind) {
        var value=SubmissionJson.object().put("commandVersion",1).put("commandType",kind).put("commandId",UUID.randomUUID().toString())
            .put("warehouseId",seed.warehouse()).put("submissionId",seed.id()).put("reason",kind.equals("NORMAL_CONFIRM")?"":"合成内部事务验收");
        for(String key:List.of("source","workRound","authorizationId","freezeDeviceId","sourceRecoveryEpoch"))value.set(key,seed.body.get(key));
        value.put("transportRecoveryEpoch","1");value.set("expected",expected(seed));return value;
    }

    private ObjectNode expected(LedgerVerifySeed seed) {
        var result=SubmissionJson.object();
        var values=jdbc.queryForMap("SELECT BIN_TO_UUID(c.canonical_order_id) canonicalOrderId,c.mapping_version mappingVersion,"
            +"BIN_TO_UUID(c.current_submission_id) currentSubmissionId,c.work_round scopeRound,l.slot_version slotVersion,"
            +"d.decision_version decisionVersion,o.current_revision targetRevision FROM submission s JOIN submission_scope c ON c.id=s.scope_id "
            +"JOIN temporary_decision_state d ON d.scope_id=c.id LEFT JOIN outbound_order o ON o.id=c.canonical_order_id "
            +"LEFT JOIN order_submission_slot l ON l.order_id=c.canonical_order_id WHERE s.id=UUID_TO_BIN(?)",seed.id());
        values.forEach((name,value)->{if(value==null)result.putNull(name);else result.put(name,value.toString());});
        String received=hash("submission_payload",seed.id());
        result.put("receivedPayloadSha256",received).put("declaredUploadSha256",hash("submission_upload",seed.id()))
            .put("planSha256",received==null?null:seed.body.path("planSha256").asText()).put("userAuthVersion",seed.tokens().path("userAuthVersion").asText());
        for(String name:List.of("candidateContextSha256","evidenceSetId","evidenceSetSha256","verificationId","verificationVersion",
            "verificationAlgorithmVersion","dispositionManifestSha256","dispositionManifestVersion","retainedManifestSha256","retainedManifestVersion"))result.putNull(name);
        return result;
    }

    private String hash(String table,String submission) {
        var rows=jdbc.queryForList("SELECT payload_sha256 FROM "+table+" WHERE submission_id=UUID_TO_BIN(?)",String.class,submission);
        return rows.isEmpty()?null:rows.getFirst();
    }
}
