package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.temporary.TemporaryStore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;
import org.springframework.context.ConfigurableApplicationContext;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Original device records include saved formal/TEMP work plus a pending manual capture. */
final class E2aUnsubmittedRecords {
    final List<ObjectNode> records = new ArrayList<>(), events = new ArrayList<>();
    final UUID temporaryClient = UUID.randomUUID(), pendingClient = UUID.randomUUID();
    private final E2aUnsubmittedPreparation p;

    E2aUnsubmittedRecords(E2aUnsubmittedPreparation p, ConfigurableApplicationContext context) {
        this.p = p;
        var formal = capture(3, p.formalAuthorization, p.issued.plusSeconds(20));
        formal.put("assignmentId", p.f.seed.get("assignmentId")).put("orderId", p.f.seed.get("orderId"))
            .put("warehouseId", p.f.seed.get("warehouseId")).put("assignmentEpoch", "1").put("orderRevision", 1).put("inputRevision", 1);
        add(formal, true);
        var authorization = context.getBean(TemporaryStore.class).get(p.temporaryAuthorization);
        var temp = capture(4, p.temporaryAuthorization, authorization.issuedAt().plusSeconds(1));
        temp.put("clientOrderId", temporaryClient.toString()).put("authorizationEpoch", "2").put("orderRevision", "1");
        add(temp, true);
        var pending = capture(5, p.temporaryAuthorization, authorization.issuedAt().plusSeconds(1));
        pending.put("clientOrderId", pendingClient.toString()).put("authorizationEpoch", "2").put("orderRevision", "1")
            .put("state", "WAITING_CONFIRM").putNull("confirmedAt");
        add(pending, false);
    }

    private ObjectNode capture(long sequence, UUID authorization, Instant confirmed) {
        return object().put("id", UUID.randomUUID().toString()).put("originDeviceId", p.f.seed.get("deviceId"))
            .put("operatorId", p.f.seed.get("operatorId")).put("orderScanSeq", "1").put("deviceCaptureSeq", Long.toString(sequence))
            .put("rawInput", text(p.inventoryRow, "barcode")).put("barcode", text(p.inventoryRow, "barcode"))
            .put("inputSource", "MANUAL").put("confirmationPolicy", "MANUAL").put("receivedWallMs", "1")
            .put("receivedElapsedMs", "1").put("receivedBoot", 1).put("authorizationId", authorization.toString())
            .put("snapshotId", p.snapshot).put("sourceRecoveryEpoch", "1").put("expectedDispatchCycle", "1")
            .put("state", "COMMITTED").put("error", "").put("confirmedAt", confirmed.toString()).putNull("failureAckAt");
    }

    private void add(ObjectNode capture, boolean saved) {
        records.add(capture);
        event(capture, "RECEIVED"); event(capture, "WAITING_CONFIRM");
        if (!saved) return;
        event(capture, "CONFIRM_REQUESTED"); event(capture, "SAVED");
        var scan = p.inventoryRow.deepCopy().put("captureId", text(capture, "id")).put("orderScanSeq", "1")
            .put("baleId", p.f.priorBale.toString()).put("expectedDispatchCycle", "1")
            .put("savedAt", Instant.parse(text(capture, "confirmedAt")).plusMillis(1).toString())
            .put("localSeq", Integer.toString(events.size())).put("status", "ACTIVE");
        records.add(scan);
    }

    private void event(ObjectNode capture, String type) {
        var row = object().put("id", UUID.randomUUID().toString()).put("captureId", text(capture, "id"))
            .put("originDeviceId", text(capture, "originDeviceId")).put("operatorId", text(capture, "operatorId"))
            .put("localSeq", Integer.toString(events.size() + 1)).put("type", type).put("detail", "")
            .put("wallMs", "1").put("elapsedMs", "1").put("boot", 1);
        if (capture.has("clientOrderId")) {
            row.put("authorizationId", text(capture, "authorizationId"));
            row.set("source", object().put("kind", "TEMP").put("creatorDeviceId", text(capture, "originDeviceId"))
                .put("clientOrderId", text(capture, "clientOrderId")));
        } else row.put("assignmentId", text(capture, "assignmentId"));
        events.add(row);
    }
}
