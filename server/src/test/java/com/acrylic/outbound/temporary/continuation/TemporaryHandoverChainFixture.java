package com.acrylic.outbound.temporary.continuation;

import com.acrylic.outbound.handover.*;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Three device qualifications with exact cumulative facts and overlapping device-local counters. */
final class TemporaryHandoverChainFixture {
    final TemporaryRoundEvidenceFixture source = new TemporaryRoundEvidenceFixture();
    final Round first = new Round(source.input().previous(), source.input().originalPlan(), source.original);
    final Round second = second();
    final Round third = third();
    final TemporaryHandoverEvidence evidence = new TemporaryHandoverEvidence(new TemporarySubmissionEvidence());

    TemporaryEvidenceOrigins secondOrigins() {
        return evidence.validate(input(first, second), second.body(), TemporaryEvidenceOrigins.initial(first.head(), first.plan()));
    }

    TemporaryEvidenceOrigins validate() {
        return evidence.validate(input(second, third), third.body(), secondOrigins());
    }

    private Round second() {
        var body = source.body.deepCopy(); body.remove("temporaryContinuation");
        var authorization = authorization(source.newAuthorization.id(), "2", "2026-10-06");
        identity(body, authorization, 2); reference(body, first);
        ownFacts(body, 2, 7, authorization);
        ((ObjectNode) body.path("scans").get(1)).put("localSeq", "3");
        return round(body, authorization);
    }

    private Round third() {
        var body = second.body().deepCopy(); var authorization = authorization(UUID.randomUUID(), "3", "2026-10-07");
        identity(body, authorization, 3); reference(body, second);
        var capture = ((ObjectNode) second.body().path("captures").get(2)).deepCopy();
        var id = UUID.randomUUID().toString(); capture.put("id", id).put("orderScanSeq", "4").put("rawInput", "00000000004");
        capture.put("barcode", "00000000004"); array(body, "captures").add(capture);
        var scan = ((ObjectNode) second.body().path("scans").get(1)).deepCopy();
        scan.put("captureId", id).put("orderScanSeq", "4").put("barcode", "00000000004");
        scan.put("baleId", UUID.randomUUID().toString()).put("savedAt", "2026-10-07T00:01:00Z"); array(body, "scans").add(scan);
        for (int index = 7; index < 11; index++) {
            var event = ((ObjectNode) second.body().path("events").get(index)).deepCopy();
            event.put("id", UUID.randomUUID().toString()).put("captureId", index == 10 ? null : id);
            array(body, "events").add(event);
        }
        ownFacts(body, 3, 11, authorization);
        body.put("captureHighWatermark", "4").put("expectedCount", "3");
        ((ObjectNode) body.get("captureSummary")).put("received", "4").put("saved", "3");
        return round(body, authorization);
    }

    private TemporaryStore.Stored authorization(UUID id, String epoch, String day) {
        return new TemporaryStore.Stored(id, source.warehouse, UUID.randomUUID(), UUID.randomUUID(), epoch, "1", "1", "1",
            Instant.parse(day + "T00:00:00Z"), Instant.parse(day + "T01:00:00Z"), null);
    }

    private void identity(ObjectNode body, TemporaryStore.Stored a, int number) {
        body.put("submissionId", UUID.randomUUID().toString()).put("authorizationId", a.id().toString())
            .put("authorizationEpoch", a.authorizationEpoch()).put("freezeDeviceId", a.deviceId().toString())
            .put("workRound", Integer.toString(number)).put("closedAt", a.issuedAt().plusSeconds(300).toString());
        var client = UUID.randomUUID().toString();
        ((ObjectNode) body.get("source")).put("creatorDeviceId", a.deviceId().toString()).put("clientOrderId", client);
        ((ObjectNode) body.get("temporaryOrder")).put("creatorDeviceId", a.deviceId().toString())
            .put("clientOrderId", client).put("authorizationId", a.id().toString());
        body.put("planSha256", sha(body.get("temporaryOrder")));
    }

    private void reference(ObjectNode body, Round previous) {
        body.putObject("temporaryHandover").put("version", 1).put("ticketId", UUID.randomUUID().toString())
            .put("caseId", UUID.randomUUID().toString()).put("predecessorSubmissionId", previous.head().control().id().toString())
            .put("predecessorPayloadSha256", sha(previous.body())).put("sourcePrefixSha256", "a".repeat(64));
    }

    private void ownFacts(ObjectNode body, int captureIndex, int eventIndex, TemporaryStore.Stored a) {
        ((ObjectNode) body.path("captures").get(captureIndex)).put("originDeviceId", a.deviceId().toString())
            .put("operatorId", a.userId().toString()).put("authorizationId", a.id().toString())
            .put("authorizationEpoch", a.authorizationEpoch()).put("clientOrderId", text(body.path("source"), "clientOrderId"))
            .put("deviceCaptureSeq", "1").put("confirmedAt", a.issuedAt().plusSeconds(30).toString());
        for (int index = eventIndex; index < array(body, "events").size(); index++) {
            var event = (ObjectNode) body.path("events").get(index);
            event.put("originDeviceId", a.deviceId().toString()).put("operatorId", a.userId().toString())
                .put("authorizationId", a.id().toString()).put("localSeq", Integer.toString(index - eventIndex + 1));
            event.set("source", body.get("source").deepCopy());
            if (text(event, "type").equals("FROZEN")) event.put("detail", text(body, "submissionId"));
        }
    }

    private Round round(ObjectNode body, TemporaryStore.Stored a) {
        var head = new SubmissionStore.Head(TemporaryHandoverControl.read(body).control(), a.userId(), a.warehouseId(), "ABORTED", null, null, null);
        var plan = new TemporarySubmissionPlan.Plan(a, object(), body.get("temporaryOrder"), List.of());
        return new Round(head, plan, body);
    }

    private TemporaryHandoverEvidence.Input input(Round previous, Round current) {
        return new TemporaryHandoverEvidence.Input(previous.head(), current.head(), previous.plan(), current.plan(), previous.body());
    }

    record Round(SubmissionStore.Head head, TemporarySubmissionPlan.Plan plan, ObjectNode body) { }
}
