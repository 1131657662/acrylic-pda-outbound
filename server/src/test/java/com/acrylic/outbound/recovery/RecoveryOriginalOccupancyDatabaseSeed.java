package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Adapts known fresh migration fixture IDs; no private-key file, previous instance or historical fact creation. */
final class RecoveryOriginalOccupancyDatabaseSeed {
    private RecoveryOriginalOccupancyDatabaseSeed() { }

    static RecoverySourceStateFixture seed(JdbcTemplate jdbc, Map<String,String> seed) throws Exception {
        var id = new RecoverySourceIdentity("TEMP", uuid(seed, "warehouseId"), uuid(seed, "mappedScopeId"), uuid(seed, "mappedSubmissionId"),
            uuid(seed, "deviceId"), uuid(seed, "operatorId"), uuid(seed, "authorizationId"), uuid(seed, "orderId"),
            uuid(seed, "deviceId"), uuid(seed, "mappedClientOrderId"), "1", 1, 7, 12, 9);
        var state = new RecoverySourceStateFixture(RecoverySourceFixture.original(id));
        String bale = seed.get("reservedBaleId");
        jdbc.update("UPDATE bale SET barcode=UPPER(barcode) WHERE id=UUID_TO_BIN(?)", bale);
        String barcode = jdbc.queryForObject("SELECT barcode FROM bale WHERE id=UUID_TO_BIN(?)", String.class, bale);
        String cycle = jdbc.queryForObject("SELECT CAST(dispatch_cycle AS CHAR) FROM bale WHERE id=UUID_TO_BIN(?)", String.class, bale);
        ((ObjectNode)state.payload.path("captures").get(0)).put("barcode", barcode).put("expectedDispatchCycle", cycle);
        ((ObjectNode)state.payload.path("scans").get(0)).put("barcode", barcode).put("baleId", bale).put("expectedDispatchCycle", cycle);
        state.payload.put("submissionId", id.submissionId().toString());
        ((ObjectNode)state.payload.path("source")).put("creatorDeviceId", id.creatorDeviceId().toString()).put("clientOrderId", id.clientOrderId().toString());
        state.row().put("barcode", barcode).put("baleId", bale).put("dispatchCycle", cycle); state.publish();
        String hash = SubmissionJson.sha(state.payload); byte[] body = state.read().original("payload");
        jdbc.update("INSERT INTO submission_payload VALUES(UUID_TO_BIN(?),2,?,?,?,?,?,?,UTC_TIMESTAMP(3))", id.submissionId().toString(),
            hash, "submissions/" + hash + ".json", body.length, 1, 1000, 7);
        jdbc.update("UPDATE submission SET status='ABORTED',receipt_text=? WHERE id=UUID_TO_BIN(?)",
            new String(state.read().original("result"), StandardCharsets.UTF_8), id.submissionId().toString());
        jdbc.update("UPDATE bale_commitment SET state='REVIEW_RESERVED',submission_id=UUID_TO_BIN(?),order_id=UUID_TO_BIN(?),source_evidence_set_id=NULL WHERE bale_id=UUID_TO_BIN(?)",
            id.submissionId().toString(), id.canonicalOrderId().toString(), bale); return state;
    }

    private static UUID uuid(Map<String,String> seed, String field) { return UUID.fromString(seed.get(field)); }
}
