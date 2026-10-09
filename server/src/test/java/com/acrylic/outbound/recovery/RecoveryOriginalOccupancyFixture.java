package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Synthetic producer/independent-pin inputs, not original-device or physical-custody evidence. */
final class RecoveryOriginalOccupancyFixture {
    final RecoverySourceStateFixture state;
    final RecoveryDeviceRegistrationCapture.Database database;
    final ObjectNode observation;
    final Instant observed = Instant.parse("2026-09-20T02:00:00Z");
    String pin;

    RecoveryOriginalOccupancyFixture(String kind) throws Exception {
        state = new RecoverySourceStateFixture(kind);
        database = new RecoveryDeviceRegistrationCapture.Database(UUID.randomUUID(), "isolated_original", "1");
        observation = observation(state, database, observed); publish();
    }

    static ObjectNode observation(RecoverySourceStateFixture state, RecoveryDeviceRegistrationCapture.Database database, Instant time) {
        var value = SubmissionJson.object().put("formatVersion", 1).put("observationKind", "ORIGINAL_SUBMISSION_OCCUPANCY")
            .put("mysqlServerUuid", database.mysqlServerUuid().toString()).put("databaseSchema", database.schema())
            .put("sourceRecoveryEpoch", database.sourceRecoveryEpoch()).put("observedAt", time.toString())
            .put("payloadSha256", SubmissionJson.sha(state.payload))
            .put("terminalReceipt", new String(state.read().original("result"), StandardCharsets.UTF_8));
        value.set("source", RecoveryOriginalOccupancyFacts.source(state.authority.source.identity));
        value.set("occupancy", state.occupancy.deepCopy()); return value;
    }

    void publish() throws Exception {
        byte[] bytes = RecoveryControlJson.encode(observation); pin = sha256(bytes);
        Files.write(state.authority.source.materials.resolve(pin + ".json"), bytes);
    }

    RecoveryOriginalOccupancy reader() {
        var source = state.authority.source;
        return new RecoveryOriginalOccupancy(source.control.location(), source.control.checkpoint,
            new RecoveryReceiptKeys(List.of(state.authority.key.pair.getPublic().getEncoded())));
    }

    RecoveryOriginalOccupancy.Request request() {
        return new RecoveryOriginalOccupancy.Request(state.authority.source.materials, pin, database, observed);
    }

    ObjectNode head() {
        var id = state.authority.source.identity;
        return RecoveryOriginalOccupancyFacts.source(id).put("status", "ABORTED")
            .put("receipt_text", observation.path("terminalReceipt").asText()).put("payload_sha256", SubmissionJson.sha(state.payload))
            .put("currentSubmissionId", id.submissionId().toString()).put("scopeRound", "1").putNull("continuationId").putNull("supersededBy");
    }
}
