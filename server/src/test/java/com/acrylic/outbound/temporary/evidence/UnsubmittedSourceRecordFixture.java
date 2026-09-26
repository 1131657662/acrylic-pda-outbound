package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;

final class UnsubmittedSourceRecordFixture {
    final ObjectNode capture = SubmissionJson.object();
    final ObjectNode scan = SubmissionJson.object();
    final List<JsonNode> events = new ArrayList<>();
    final UnsubmittedSourceAuthority.Origin origin;

    UnsubmittedSourceRecordFixture() {
        for (String field : List.of("id", "clientOrderId", "originDeviceId", "operatorId", "authorizationId"))
            capture.put(field, UUID.randomUUID().toString());
        capture.put("orderScanSeq", "1").put("deviceCaptureSeq", "5").put("rawInput", "  abc12345678\r\n")
            .put("barcode", "ABC12345678").put("inputSource", "MANUAL").put("confirmationPolicy", "MANUAL")
            .put("receivedWallMs", "1").put("receivedElapsedMs", "1").put("receivedBoot", 1)
            .put("expectedDispatchCycle", "2").put("state", "COMMITTED").put("error", "")
            .put("confirmedAt", "2026-09-24T01:00:00Z").putNull("failureAckAt");
        scan.put("captureId", capture.path("id").asText()).put("orderScanSeq", "1").put("barcode", "ABC12345678")
            .put("baleId", UUID.randomUUID().toString()).put("expectedDispatchCycle", "2").put("localSeq", "4")
            .put("deliveryCode", "123").put("model", "M").put("name", "N").put("rawWeight", "1.000")
            .put("weightGrams", "1000").put("savedAt", "2026-09-24T01:00:01Z").put("status", "ACTIVE");
        for (String type : List.of("RECEIVED", "WAITING_CONFIRM", "CONFIRM_REQUESTED", "SAVED")) event(type);
        var parts = new UnsubmittedSourcePreparation.Parts(SubmissionJson.object(), Map.of("ABC12345678", scan.deepCopy()),
            Map.of("ABC12345678", SubmissionJson.object().put("baleId", scan.path("baleId").asText()).put("dispatchCycle", "2")));
        origin = new UnsubmittedSourceAuthority.Origin(Instant.parse("2026-09-24T00:00:00Z"), Instant.parse("2026-09-24T02:00:00Z"), parts);
    }

    void event(String type) {
        var event = SubmissionJson.object().put("captureId", capture.path("id").asText()).put("type", type).put("detail", "")
            .put("originDeviceId", capture.path("originDeviceId").asText()).put("operatorId", capture.path("operatorId").asText())
            .put("authorizationId", capture.path("authorizationId").asText()).put("localSeq", Integer.toString(events.size() + 1));
        event.set("source", SubmissionJson.object().put("kind", "TEMP").put("creatorDeviceId", capture.path("originDeviceId").asText())
            .put("clientOrderId", capture.path("clientOrderId").asText()));
        events.add(event);
    }

    void validate() { UnsubmittedSourceRecords.validate(capture, scan, events, origin); }
}
