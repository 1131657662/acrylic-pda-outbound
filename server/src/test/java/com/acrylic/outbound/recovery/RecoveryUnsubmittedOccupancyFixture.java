package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Native development E2A with an ABORTED receipt that contains no payload hash. */
final class RecoveryUnsubmittedOccupancyFixture {
    final RecoveryOriginalEvidenceFixture evidence;
    final RecoveryInitialFixture initial;
    final UUID setId;
    final RecoveryDeviceRegistrationCapture.Database database;
    final ObjectNode observation;
    final Instant time = Instant.parse("2026-09-20T02:00:00Z");
    String pin;

    RecoveryUnsubmittedOccupancyFixture() throws Exception {
        this(new RecoveryOriginalEvidenceFixture(), UUID.randomUUID(),
            new RecoveryDeviceRegistrationCapture.Database(UUID.randomUUID(), "isolated_original", "1"));
    }

    RecoveryUnsubmittedOccupancyFixture(RecoveryOriginalEvidenceFixture evidence, UUID setId,
            RecoveryDeviceRegistrationCapture.Database database) throws Exception {
        this.evidence = evidence; this.initial = evidence.initial; this.setId = setId; this.database = database;
        initial.state.payload.removeAll(); initial.state.payload.setAll(evidence.frozen);
        ((ObjectNode)initial.state.authority.source.manifest.path("source")).put("captureHighWatermark", Long.toString(evidence.identity.captureHighWatermark()))
            .put("auditThroughSeq", Long.toString(evidence.identity.auditThroughSeq() - 1)).put("orderScanHighWatermark", Long.toString(evidence.identity.orderScanHighWatermark()));
        initial.state.occupancy.put("sourceEvidenceSetId", setId.toString());
        initial.state.row().put("sourceEvidenceSetId", setId.toString()).put("ownerState", "SOURCE_RESERVED").putNull("ownerSubmissionId").putNull("ownerOrderId");
        initial.publish(); nullHashTerminal();
        var expected = RecoveryUnsubmittedEvidence.expected(evidence.request()); var proof = expected.proof();
        var set = SubmissionJson.object().put("id", setId.toString()).put("manifestBytesSha256", sha256(expected.manifestBytes())).put("setSha256", proof.setHash())
            .put("captureHighWatermark", Long.toString(proof.identity().captureHighWatermark())).put("auditThroughSeq", Long.toString(proof.identity().auditThroughSeq()))
            .put("orderScanHighWatermark", Long.toString(proof.identity().orderScanHighWatermark())).put("itemCount", Integer.toString(proof.itemCount()))
            .put("evidenceItemsSha256", expected.itemsSha256()).put("stopProofSha256", sha256(expected.stopProofBytes()));
        observation = SubmissionJson.object().put("formatVersion", 1).put("observationKind", "ORIGINAL_UNSUBMITTED_TEMP_OCCUPANCY")
            .put("mysqlServerUuid", database.mysqlServerUuid().toString()).put("databaseSchema", database.schema()).put("sourceRecoveryEpoch", "1")
            .put("observedAt", time.toString()).put("payloadSha256", proof.payloadSha256())
            .put("terminalReceipt", new String(initial.source().original("result"), StandardCharsets.UTF_8));
        observation.set("source", RecoveryOriginalOccupancyFacts.source(initial.expected()));
        observation.set("occupancy", initial.state.occupancy.deepCopy()); observation.set("evidenceSet", set); publish();
    }

    void nullHashTerminal() throws Exception {
        var receipt = initial.state.authority.key.receipt(initial.state.authority.source, "ABORTED").putNull("payloadSha256").put("payloadPresent", false);
        initial.state.object("result", initial.state.authority.key.signed(receipt));
    }

    void publish() throws Exception {
        byte[] bytes = RecoveryControlJson.encode(observation); pin = sha256(bytes);
        Files.write(initial.state.authority.source.materials.resolve(pin + ".json"), bytes);
    }

    RecoveryUnsubmittedOccupancy reader() {
        var source = initial.state.authority.source;
        return new RecoveryUnsubmittedOccupancy(source.control.location(), source.control.checkpoint, initial.keys());
    }
    RecoveryUnsubmittedOccupancy.Request request() {
        return new RecoveryUnsubmittedOccupancy.Request(initial.state.authority.source.materials, pin, database, time);
    }
    RecoveryUnsubmittedOccupancy.Proof read() { return reader().read(request(), initial.source(), evidence.request()); }
}
