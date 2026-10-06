package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SourceEvidenceIntakeCrossSourceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SubmissionFiles files = mock(SubmissionFiles.class);
    private final SourceEvidenceIntakeBusinessFacts facts = new SourceEvidenceIntakeBusinessFacts(jdbc, files, org.mockito.Mockito.mock(SourceEvidenceIntakeLocalPayload.class), null);
    private final SourceEvidenceIntakePageObjects.Binding binding = new SourceEvidenceIntakePageObjects.Binding(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    private final UUID otherSubmission = UUID.randomUUID();
    private final ObjectNode earlier = capture(1), current = capture(2);
    private final ObjectNode priorScan = scan(earlier, "ACTIVE", 3), ownScan = scan(current, "VOID", 4);
    private final ObjectNode priorPayload = payload(earlier, priorScan);

    @BeforeEach void storedFacts() {
        when(jdbc.queryForList(contains("payload_sha256"), eq(String.class), any(Object[].class)))
            .thenAnswer(call -> Arrays.equals((byte[]) call.getArgument(2), Ids.bytes(binding.submissionId()))
                ? List.of("a".repeat(64)) : List.of("b".repeat(64)));
        when(files.read("a".repeat(64))).thenReturn(bytes(payload(current, ownScan)));
        when(files.read("b".repeat(64))).thenAnswer(call -> bytes(priorPayload));
        when(jdbc.queryForList(contains("BIN_TO_UUID(c.submission_id)"), eq(String.class), any(Object[].class)))
            .thenReturn(List.of(otherSubmission.toString()));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
    }

    @Test void fullDevicePrefixCanContainEarlierFormalCaptureWithoutReservingItsBale() {
        var accepted = facts.require(binding, items(), 2);

        assertThat(accepted).containsExactly(item("SCAN", ownScan));
        verify(jdbc, never()).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void missingOtherCaptureFailsClosed() {
        when(jdbc.queryForList(contains("BIN_TO_UUID(c.submission_id)"), eq(String.class), any(Object[].class)))
            .thenReturn(List.of());

        rejected("E2A_OTHER_CAPTURE_NOT_STORED", items());
    }

    @Test void missingOtherPayloadFailsClosed() {
        when(files.read("b".repeat(64))).thenThrow(new IllegalStateException("missing original"));

        rejected("E2A_SOURCE_PAYLOAD_UNAVAILABLE", items());
    }

    @ParameterizedTest @ValueSource(strings = {"state", "originDeviceId", "deviceCaptureSeq"})
    void forgedOtherCaptureCannotReplaceStoredOriginal(String field) {
        String value = switch (field) {
            case "state" -> "ABORTED";
            case "originDeviceId" -> UUID.randomUUID().toString();
            default -> "9";
        };
        var forged = earlier.deepCopy().put(field, value);

        rejected("E2A_OTHER_CAPTURE_FACT_CONFLICT", List.of(item("CAPTURE", current),
            item("SCAN", ownScan), item("CAPTURE", forged), item("SCAN", priorScan)));
    }

    @ParameterizedTest @ValueSource(strings = {"baleId", "expectedDispatchCycle", "status", "localSeq"})
    void forgedOtherScanCannotReplaceStoredOriginal(String field) {
        String value = switch (field) {
            case "baleId" -> UUID.randomUUID().toString();
            case "status" -> "VOID";
            default -> "9";
        };
        var forged = priorScan.deepCopy().put(field, value);

        rejected("E2A_OTHER_SCAN_FACT_CONFLICT", List.of(item("CAPTURE", current),
            item("SCAN", ownScan), item("CAPTURE", earlier), item("SCAN", forged)));
    }

    @Test void omittedOtherScanCannotHideOriginalActiveScan() {
        rejected("E2A_OTHER_SCAN_FACT_COUNT_CONFLICT", List.of(item("CAPTURE", current),
            item("SCAN", ownScan), item("CAPTURE", earlier)));
    }

    @Test void duplicatedOtherScanCannotCompleteFacts() {
        var values = new java.util.ArrayList<>(items());
        values.add(item("SCAN", priorScan));

        rejected("E2A_OTHER_SCAN_FACT_DUPLICATE", values);
    }

    @Test void currentSourceCannotMasqueradeAsOtherSource() {
        when(jdbc.queryForList(contains("BIN_TO_UUID(c.submission_id)"), eq(String.class), any(Object[].class)))
            .thenReturn(List.of(binding.submissionId().toString()));

        rejected("E2A_OTHER_CAPTURE_SOURCE_CONFLICT", items());
    }

    @Test void otherScanWithoutItsCaptureFailsClosed() {
        rejected("E2A_OTHER_SCAN_CAPTURE_MISSING", List.of(item("CAPTURE", current),
            item("SCAN", ownScan), item("SCAN", priorScan)));
    }

    @Test void otherCaptureWithoutStoredScanDoesNotInventOne() {
        priorPayload.withArray("scans").removeAll();

        var accepted = facts.require(binding, List.of(item("CAPTURE", current),
            item("SCAN", ownScan), item("CAPTURE", earlier)), 2);

        assertThat(accepted).containsExactly(item("SCAN", ownScan));
    }

    @Test void inventedScanForOtherCaptureFailsClosed() {
        priorPayload.withArray("scans").removeAll();

        rejected("E2A_OTHER_SCAN_FACT_CONFLICT", items());
    }

    @Test void duplicatedOtherCaptureFailsClosed() {
        var values = new java.util.ArrayList<>(items());
        values.add(item("CAPTURE", earlier));

        rejected("E2A_OTHER_CAPTURE_DUPLICATE", values);
    }

    @Test void duplicateStoredScanIsAmbiguousAndFailsClosed() {
        priorPayload.withArray("scans").add(priorScan.deepCopy());

        rejected("E2A_OTHER_FACT_DUPLICATE", items());
    }

    private void rejected(String code, List<E2aPageItem> values) {
        assertThatThrownBy(() -> facts.require(binding, values, 2))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.code()).isEqualTo(code));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(jdbc, never()).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class));
    }

    private List<E2aPageItem> items() {
        return List.of(item("CAPTURE", earlier), item("CAPTURE", current),
            item("SCAN", priorScan), item("SCAN", ownScan));
    }

    private ObjectNode capture(long sequence) {
        return SubmissionJson.object().put("id", UUID.randomUUID().toString())
            .put("originDeviceId", binding.creatorDeviceId().toString())
            .put("deviceCaptureSeq", Long.toString(sequence)).put("orderScanSeq", "1").put("state", "COMMITTED");
    }

    private static ObjectNode scan(JsonNode capture, String status, long sequence) {
        return SubmissionJson.object().put("captureId", capture.path("id").asText())
            .put("localSeq", Long.toString(sequence)).put("orderScanSeq", capture.path("orderScanSeq").asText())
            .put("baleId", UUID.randomUUID().toString())
            .put("expectedDispatchCycle", "1").put("status", status);
    }

    private static ObjectNode payload(JsonNode capture, JsonNode scan) {
        var result = SubmissionJson.object();
        result.putArray("captures").add(capture);
        result.putArray("scans").add(scan);
        return result;
    }

    private static E2aPageItem item(String kind, JsonNode record) {
        boolean scan = "SCAN".equals(kind);
        UUID id = SubmissionJson.uuid(record, scan ? "captureId" : "id");
        return new E2aPageItem(kind, id, "CAPTURE_RECORDS", id, SubmissionJson.sha(record),
            bytes(record).length, SubmissionJson.number(record, scan ? "localSeq" : "deviceCaptureSeq"),
            SubmissionJson.sha(record), SubmissionJson.text(record, scan ? "status" : "state"),
            scan ? SubmissionJson.uuid(record, "baleId") : null,
            scan ? SubmissionJson.number(record, "expectedDispatchCycle") : null, scan ? id : null);
    }

    private static byte[] bytes(JsonNode record) {
        return SubmissionJson.canonical(record).getBytes(StandardCharsets.UTF_8);
    }
}
