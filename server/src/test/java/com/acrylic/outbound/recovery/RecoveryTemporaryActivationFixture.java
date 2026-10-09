package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.JsonText;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.TemporaryCommands;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Native TEMP ACK declaration/response and issuer serialization, with in-memory development RSA. */
final class RecoveryTemporaryActivationFixture {
    final RecoveryTemporaryBaselineFixture baseline;
    final ObjectNode command;
    final ObjectNode proof;
    final ObjectNode receipt;

    RecoveryTemporaryActivationFixture() throws Exception {
        this(new RecoveryTemporaryBaselineFixture());
    }

    RecoveryTemporaryActivationFixture(RecoveryTemporaryBaselineFixture original) throws Exception {
        baseline = original;
        var prep = baseline.prep; var id = prep.state.authority.source.identity;
        command = SubmissionJson.object().put("bundleId", prep.receipt.path("bundleId").asText())
            .put("manifestSha256", prep.receipt.path("manifestSha256").asText()).put("authorizationEpoch", "1")
            .put("activatedAt", "2026-09-20T00:01:00.123456789Z");
        proof = SubmissionJson.object().put("iss", "acrylic-outbound").put("aud", "acrylic-pda-temporary-activation")
            .put("authorizationId", id.qualificationId().toString()).put("bundleId", prep.receipt.path("bundleId").asText())
            .put("manifestSha256", command.path("manifestSha256").asText()).put("commandSha256", digest())
            .put("serverRecoveryEpoch", id.sourceRecoveryEpoch()).put("userId", id.operatorId().toString()).put("deviceId", id.deviceId().toString())
            .put("activatedAt", "2026-09-20T00:01:00.123Z").put("acknowledgedAt", "2026-09-23T00:00:00Z");
        receipt = SubmissionJson.object(); publish();
    }

    String digest() {
        var nativeCommand = new TemporaryCommands.Ack(UUID.fromString(command.path("bundleId").asText()),
            command.path("manifestSha256").asText(), command.path("authorizationEpoch").asText(), Instant.parse(command.path("activatedAt").asText()));
        return new JsonText(new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
            .digest(nativeCommand);
    }

    void publish() throws Exception {
        var prep = baseline.prep; var key = prep.state.authority.key;
        var header = SubmissionJson.object().put("alg", "RS256").put("typ", "JWT").put("kid", key.kid);
        for (String field : List.of("authorizationId", "bundleId", "activatedAt", "acknowledgedAt")) receipt.set(field, proof.path(field));
        receipt.put("signedAck", key.token(header, proof)); write();
    }

    void write() throws Exception {
        baseline.prep.origin.putObject("activation").put("command", command.toString()).put("receipt", receipt.toString());
        baseline.prep.write();
    }

    RecoverySourceTemporaryActivation reader() {
        return new RecoverySourceTemporaryActivation(new RecoveryReceiptKeys(List.of(baseline.prep.state.authority.key.pair.getPublic().getEncoded())));
    }
}
