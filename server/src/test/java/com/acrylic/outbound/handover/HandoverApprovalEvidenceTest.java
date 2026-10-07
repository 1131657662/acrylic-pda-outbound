package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.recall.RecallStore;
import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Descriptor tests mock the signature-verification boundary; signed trust is tested separately. */
class HandoverApprovalEvidenceTest {
    private final HandoverStopStore stops = mock(HandoverStopStore.class);
    private final HandoverStopReceipts receipts = mock(HandoverStopReceipts.class);
    private final RecallStore recalls = mock(RecallStore.class);
    private final SubmissionFiles files = mock(SubmissionFiles.class);
    private final HandoverApprovalEvidence consumer = new HandoverApprovalEvidence(stops, receipts, recalls, files,
        mock(HandoverTemporaryApprovalEvidence.class));
    private final UUID kase = UUID.randomUUID(), recallId = UUID.randomUUID(), packageId = UUID.randomUUID();
    private final HandoverStopStore.Origin origin = new HandoverStopStore.Origin(id(), id(), id(), id(), id(), id(), id(), "1", 1);
    private final ObjectNode facts = SubmissionJson.object(), receipt = SubmissionJson.object(), body = SubmissionJson.object();
    private RecallStore.Head recall;
    private RecallStore.Package pack;

    @BeforeEach void evidence() {
        facts.put("caseId", kase.toString()).put("caseVersion", "2").put("inputHighWatermark", "2")
            .put("stopState", "DEVICE_ACKED").put("integrityState", "COMPLETE");
        for (String field : List.of("warehouseId", "scopeId", "submissionId", "canonicalOrderId", "sourceKind", "fromWorkRound", "toWorkRound"))
            facts.put(field, "bound-" + field);
        receipt.set("caseContext", facts.deepCopy().put("caseVersion", "1"));
        var command = SubmissionJson.object().put("commandId", id().toString()).put("expectedCaseVersion", "1")
            .put("changeRequestId", recallId.toString()).put("recoveryPackageId", packageId.toString()).put("payloadSha256", "a".repeat(64))
            .put("captureHighWatermark", "2").put("auditThroughSeq", "8");
        receipt.set("originalCommand", HandoverStopCommand.parse(kase, "1", HandoverReceiptCodec.bytes(command)).bound());
        var stop = receipt.putObject("recallStop").put("actualCount", "1").put("actualWeightGrams", "10");
        recall = new RecallStore.Head(recallId, origin.warehouse(), origin.order(), origin.assignment(), 1, "1", "STOPPED", "reason", stop.toString(), null);
        pack = new RecallStore.Package(packageId, "a".repeat(64), 1, 10, 2, 8, "[]");
        body.put("recoveryPackageId", packageId.toString()).put("changeRequestId", recallId.toString()).put("assignmentId", origin.assignment().toString())
            .put("orderId", origin.order().toString()).put("freezeDeviceId", origin.device().toString()).put("sourceRecoveryEpoch", "1")
            .put("workRound", "1").put("captureHighWatermark", "2").put("expectedCount", "1").put("expectedWeightGrams", "10");
        body.putArray("auditCoverage").addObject().put("originDeviceId", origin.device().toString()).put("throughSeq", "8");
        when(stops.origin(kase)).thenReturn(origin); when(stops.original(kase)).thenReturn(Map.of("kind", "STOP_PROOF"));
        when(stops.recovery(kase)).thenReturn(Map.of("kind", "RECOVERY_PACKAGE"));
        when(receipts.verifyEvidence(anyMap(), anyMap(), eq(origin), any())).thenReturn(receipt);
        when(recalls.get(recallId)).thenReturn(recall); when(recalls.recovery(recallId)).thenReturn(pack);
        when(files.read(pack.hash())).thenAnswer(call -> HandoverReceiptCodec.bytes(body));
    }

    @Test void consumesFullAttestedFileWithoutMutatingTheReturnedSnapshot() {
        var result = consumer.verify(facts, 2);
        assertThat(result.body()).isEqualTo(body); assertThat(result.receipt()).isEqualTo(receipt);
        ((ObjectNode) result.body()).put("workRound", "99");
        assertThat(result.body()).isEqualTo(body);
        verify(recalls, never()).lock(any());
    }

    @ParameterizedTest @ValueSource(strings={"warehouseId", "scopeId", "submissionId", "canonicalOrderId", "sourceKind", "fromWorkRound", "toWorkRound", "stopState", "integrityState", "inputHighWatermark", "caseVersion"})
    void rejectsChangedCaseDimensions(String field) {
        facts.put(field, field.equals("caseVersion") ? "1" : field.equals("inputHighWatermark") ? "9" : "changed");
        rejected("HANDOVER_EVIDENCE_CHANGED"); verifyNoInteractions(files);
    }

    @ParameterizedTest @ValueSource(strings={"recoveryPackageId", "changeRequestId", "assignmentId", "orderId", "freezeDeviceId", "sourceRecoveryEpoch", "workRound", "captureHighWatermark", "expectedCount", "expectedWeightGrams"})
    void rejectsUnboundFileDescriptors(String field) {
        body.put(field, field.endsWith("Id") ? id().toString() : "99"); rejected("HANDOVER_EVIDENCE_CHANGED");
    }

    @Test void rejectsIncompletePairBeforeVerifyingAnything() {
        when(stops.recovery(kase)).thenReturn(null); rejected("HANDOVER_STOP_NOT_PROVEN"); verifyNoInteractions(receipts, files);
    }

    @Test void rejectsWrongRequestedHighWatermark() {
        assertThatThrownBy(() -> consumer.verify(facts, 1)).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("HANDOVER_EVIDENCE_CHANGED"));
    }

    @Test void rejectsChangedRecallAndPackage() {
        when(recalls.get(recallId)).thenReturn(new RecallStore.Head(recallId, origin.warehouse(), origin.order(), origin.assignment(), 1, "1", "RESOLVED", "reason", recall.stop(), null));
        rejected("HANDOVER_EVIDENCE_CHANGED");
        when(recalls.get(recallId)).thenReturn(recall);
        when(recalls.recovery(recallId)).thenReturn(new RecallStore.Package(packageId, pack.hash(), 2, 10, 2, 8, "[]"));
        rejected("HANDOVER_EVIDENCE_CHANGED"); verifyNoInteractions(files);
    }

    @Test void refusesMissingOrDamagedDurableFile() {
        when(files.read(pack.hash())).thenThrow(new java.io.UncheckedIOException(new java.io.IOException("digest mismatch")));
        rejected("HANDOVER_STOP_FILE_UNAVAILABLE");
    }

    @Test void rejectsChangedDeviceAuditCoverage() {
        ((ObjectNode) body.path("auditCoverage").get(0)).put("throughSeq", "9"); rejected("HANDOVER_EVIDENCE_CHANGED");
    }

    private void rejected(String code) {
        assertThatThrownBy(() -> consumer.verify(facts, 2)).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.code()).isEqualTo(code));
    }
    private UUID id() { return UUID.randomUUID(); }
}
