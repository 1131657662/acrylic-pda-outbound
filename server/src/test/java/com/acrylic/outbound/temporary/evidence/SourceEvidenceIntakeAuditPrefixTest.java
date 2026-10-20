package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.audit.PdaAuditOrigins;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SourceEvidenceIntakeAuditPrefixTest {
    private final UUID device = UUID.randomUUID(), warehouse = UUID.randomUUID();
    private final UUID operator = UUID.randomUUID(), assignment = UUID.randomUUID();
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PdaAuditOrigins origins = mock(PdaAuditOrigins.class);
    private final SourceEvidenceIntakeAuditPrefix prefix = new SourceEvidenceIntakeAuditPrefix(jdbc, origins);

    @org.junit.jupiter.api.BeforeEach void authorizedOrigins() {
        when(origins.verify(eq(device), eq(warehouse), any()))
            .thenReturn(new PdaAuditOrigins.Origin(assignment, null, null, operator.toString(), "2"));
    }

    @Test void checksFormalAndTemporaryEventsInOneDeviceSequence() {
        var formal = event(1);
        var temporary = event(2);
        UUID authorization = UUID.randomUUID(), client = UUID.randomUUID();
        temporary.remove("assignmentId");
        temporary.set("source", SubmissionJson.object().put("kind", "TEMP")
            .put("creatorDeviceId", device.toString()).put("clientOrderId", client.toString()));
        temporary.put("authorizationId", authorization.toString());
        var first = row(formal, 1);
        var second = row(temporary, 2);
        second.put("assignment_id", null);
        second.put("temporary_authorization_id", Ids.bytes(authorization));
        second.put("client_order_id", Ids.bytes(client));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(first, second));

        prefix.require(device, warehouse, 3, 2);
    }

    @Test void absentOrOutOfOrderDeviceEventsFailClosed() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        assertUnverifiable(1);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row(event(2), 2)));
        assertUnverifiable(2);
    }

    @Test void modifiedHashAndForeignWarehouseFailClosed() {
        var saved = row(event(1), 1);
        saved.put("record_sha256", "f".repeat(64));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(saved));
        assertUnverifiable(1);
        saved.put("record_sha256", SubmissionJson.sha(SubmissionJson.parse((String) saved.get("payload"))));
        saved.put("warehouse_id", Ids.bytes(UUID.randomUUID()));
        assertUnverifiable(1);
    }

    @Test void foreignOriginalIdentityAndFutureRecoveryEpochFailClosed() {
        var saved = row(event(1), 1);
        saved.put("operator_id", Ids.bytes(UUID.randomUUID()));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(saved));
        assertUnverifiable(1);
        saved.put("operator_id", Ids.bytes(operator));
        saved.put("source_recovery_epoch", 4L);
        assertUnverifiable(1);
    }

    @Test void originalAuthorizationEpochMustMatchStoredRow() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row(event(1), 1)));
        when(origins.verify(eq(device), eq(warehouse), any()))
            .thenReturn(new PdaAuditOrigins.Origin(assignment, null, null, operator.toString(), "1"));

        assertUnverifiable(1);
    }

    @Test void signedStopEndpointMustBeTheTemporaryAuditEvent() {
        var event = event(1);
        UUID authorization = UUID.randomUUID(), client = UUID.randomUUID(), submission = UUID.randomUUID();
        event.put("detail", submission.toString());
        event.remove("assignmentId");
        event.set("source", SubmissionJson.object().put("kind", "TEMP")
            .put("creatorDeviceId", device.toString()).put("clientOrderId", client.toString()));
        event.put("authorizationId", authorization.toString());
        var stored = row(event, 1);
        stored.put("assignment_id", null);
        stored.put("temporary_authorization_id", Ids.bytes(authorization));
        stored.put("client_order_id", Ids.bytes(client));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(stored));

        when(jdbc.queryForList(contains("WHERE s.id=?"), eq(String.class), any(Object[].class)))
            .thenReturn(List.of(client.toString()));
        prefix.requireStopEvent(device, warehouse, operator, authorization, 3, 1, event.path("id").asText(), submission);
        assertThatThrownBy(() -> prefix.requireStopEvent(device, warehouse, operator, authorization,
            3, 1, event.path("id").asText(), UUID.randomUUID())).isInstanceOf(BusinessException.class);
        when(jdbc.queryForList(contains("WHERE s.id=?"), eq(String.class), any(Object[].class)))
            .thenReturn(List.of(UUID.randomUUID().toString()));
        assertThatThrownBy(() -> prefix.requireStopEvent(device, warehouse, operator, authorization,
            3, 1, event.path("id").asText(), submission)).isInstanceOf(BusinessException.class);
        when(jdbc.queryForList(contains("WHERE s.id=?"), eq(String.class), any(Object[].class)))
            .thenReturn(List.of(client.toString()));
        assertThatThrownBy(() -> prefix.requireStopEvent(device, warehouse, operator, authorization,
            3, 1, UUID.randomUUID().toString(), submission)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> prefix.requireStopEvent(device, warehouse, operator, UUID.randomUUID(),
            3, 1, event.path("id").asText(), submission)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> prefix.requireStopEvent(device, warehouse, UUID.randomUUID(), authorization,
            3, 1, event.path("id").asText(), submission)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> prefix.requireStopEvent(device, warehouse, operator, authorization,
            3, 2, event.path("id").asText(), submission)).isInstanceOf(BusinessException.class);
        event.put("type", "SCAN");
        stored.put("payload", SubmissionJson.canonical(event));
        stored.put("record_sha256", SubmissionJson.sha(event));
        assertThatThrownBy(() -> prefix.requireStopEvent(device, warehouse, operator, authorization,
            3, 1, event.path("id").asText(), submission)).isInstanceOf(BusinessException.class);
    }

    @Test void pageAuditRawBytesMustMatchTheStoredDeviceEvent() {
        var event = event(1);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row(event, 1)));
        byte[] original = SubmissionJson.canonical(event).getBytes(StandardCharsets.UTF_8);
        var item = item(event, SubmissionJson.sha(event));

        prefix.requireItem(item, original, device, warehouse, 3);
        assertThatThrownBy(() -> prefix.requireItem(item(event, "f".repeat(64)), original,
            device, warehouse, 3)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("E2A_AUDIT_PREFIX_UNVERIFIABLE"));
        assertThatThrownBy(() -> prefix.requireItem(item, (" " + SubmissionJson.canonical(event))
            .getBytes(StandardCharsets.UTF_8), device, warehouse, 3)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> prefix.requireItem(item(event(1), SubmissionJson.sha(event)), original,
            device, warehouse, 3)).isInstanceOf(BusinessException.class);
    }

    @Test void signedArchiveEventMustMatchTheOriginalDatabasePayload() {
        var event = event(1);
        var saved = row(event, 1);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(saved));

        prefix.requireArchiveEvent(event, device, warehouse, 3);
        var altered = event.deepCopy();
        altered.put("detail", "replaced");
        assertThatThrownBy(() -> prefix.requireArchiveEvent(altered, device, warehouse, 3))
            .isInstanceOf(BusinessException.class);
        saved.put("source_recovery_epoch", 4L);
        assertThatThrownBy(() -> prefix.requireArchiveEvent(event, device, warehouse, 3))
            .isInstanceOf(BusinessException.class);
    }

    private E2aPageItem item(ObjectNode event, String hash) {
        byte[] original = SubmissionJson.canonical(event).getBytes(StandardCharsets.UTF_8);
        return new E2aPageItem("AUDIT", SubmissionJson.uuid(event, "id"), "AUDIT_RECORDS",
            UUID.randomUUID(), ImportFiles.sha256(original), original.length, 1, hash, "SAVED", null, null, null);
    }


    private void assertUnverifiable(long through) {
        assertThatThrownBy(() -> prefix.require(device, warehouse, 3, through))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("E2A_AUDIT_PREFIX_UNVERIFIABLE"));
    }

    private ObjectNode event(long sequence) {
        return SubmissionJson.object().put("id", UUID.randomUUID().toString()).putNull("captureId")
            .put("originDeviceId", device.toString()).put("operatorId", operator.toString())
            .put("localSeq", Long.toString(sequence)).put("type", "STOP").put("detail", "saved")
            .put("wallMs", "1").put("elapsedMs", "1").put("boot", 1)
            .put("assignmentId", assignment.toString());
    }

    private Map<String, Object> row(ObjectNode event, long sequence) {
        var saved = new HashMap<String, Object>();
        saved.put("id", Ids.bytes(SubmissionJson.uuid(event, "id")));
        saved.put("origin_device_id", Ids.bytes(device));
        saved.put("local_seq", sequence);
        saved.put("warehouse_id", Ids.bytes(warehouse));
        saved.put("operator_id", Ids.bytes(operator));
        saved.put("source_recovery_epoch", 2L);
        saved.put("record_sha256", SubmissionJson.sha(event));
        saved.put("payload", SubmissionJson.canonical(event));
        saved.put("assignment_id", Ids.bytes(assignment));
        return saved;
    }
}
