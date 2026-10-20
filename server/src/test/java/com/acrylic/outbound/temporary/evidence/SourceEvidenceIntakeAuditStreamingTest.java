package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.audit.PdaAuditOrigins;
import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises the real streaming verifier; JDBC rows are the mocked infrastructure boundary. */
class SourceEvidenceIntakeAuditStreamingTest {
    private final UUID device = UUID.randomUUID(), warehouse = UUID.randomUUID();
    private final UUID operator = UUID.randomUUID(), assignment = UUID.randomUUID();
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PdaAuditOrigins origins = mock(PdaAuditOrigins.class);
    private final SourceEvidenceIntakeAuditPrefix prefix = new SourceEvidenceIntakeAuditPrefix(jdbc, origins);
    private final ObjectNode event = event();

    @BeforeEach void storedOriginalPrefix() {
        when(origins.verify(eq(device), eq(warehouse), any())).thenReturn(
            new PdaAuditOrigins.Origin(assignment, null, null, operator.toString(), "2"));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(saved()));
    }

    @ParameterizedTest
    @CsvSource({"pda_audit_event,true", "pda_audit_event,false",
        "submitted_event,true", "submitted_event,false"})
    void conflictingIdOrDeviceSequenceFailsClosed(String table, boolean byId) throws Exception {
        streamed(table, byId, "f".repeat(64));

        assertUnverifiable();
    }

    @ParameterizedTest
    @CsvSource({"pda_audit_event,true", "pda_audit_event,false",
        "submitted_event,true", "submitted_event,false"})
    void sameOriginalDigestRemainsIdempotent(String table, boolean byId) throws Exception {
        streamed(table, byId, SubmissionJson.sha(event));

        prefix.require(device, warehouse, 3, 1);

        verify(jdbc).query(contains(query(table, byId)), any(RowCallbackHandler.class), any(Object[].class));
    }

    @Test void conflictAfterMatchingStreamRowStillFailsClosed() throws Exception {
        doAnswer(call -> {
            var handler = (RowCallbackHandler) call.getArgument(1);
            handler.processRow(result(true, SubmissionJson.sha(event)));
            handler.processRow(result(true, "f".repeat(64)));
            return null;
        }).when(jdbc).query(contains(query("submitted_event", true)), any(RowCallbackHandler.class), any(Object[].class));

        assertUnverifiable();
    }

    @Test void jdbcFailureDoesNotTurnIntoVerifiedPrefix() {
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic JDBC failure"))
            .when(jdbc).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));

        assertUnverifiable();
    }

    private void streamed(String table, boolean byId, String digest) throws Exception {
        doAnswer(call -> {
            ((RowCallbackHandler) call.getArgument(1)).processRow(result(byId, digest));
            return null;
        }).when(jdbc).query(contains(query(table, byId)), any(RowCallbackHandler.class), any(Object[].class));
    }

    private ResultSet result(boolean byId, String digest) throws SQLException {
        var row = mock(ResultSet.class);
        if (byId) when(row.getString(1)).thenReturn(SubmissionJson.uuid(event, "id").toString());
        else when(row.getLong(1)).thenReturn(1L);
        when(row.getString(2)).thenReturn(digest);
        return row;
    }

    private String query(String table, boolean byId) {
        return "FROM " + table + (byId ? " WHERE id IN" : " WHERE origin_device_id=?");
    }

    private void assertUnverifiable() {
        assertThatThrownBy(() -> prefix.require(device, warehouse, 3, 1))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("E2A_AUDIT_PREFIX_UNVERIFIABLE"));
    }

    private ObjectNode event() {
        return SubmissionJson.object().put("id", UUID.randomUUID().toString()).putNull("captureId")
            .put("originDeviceId", device.toString()).put("operatorId", operator.toString())
            .put("localSeq", "1").put("type", "STOP").put("detail", "saved")
            .put("wallMs", "1").put("elapsedMs", "1").put("boot", 1)
            .put("assignmentId", assignment.toString());
    }

    private Map<String, Object> saved() {
        var row = new HashMap<String, Object>();
        row.put("id", Ids.bytes(SubmissionJson.uuid(event, "id")));
        row.put("origin_device_id", Ids.bytes(device)); row.put("local_seq", 1L);
        row.put("warehouse_id", Ids.bytes(warehouse)); row.put("operator_id", Ids.bytes(operator));
        row.put("source_recovery_epoch", 2L); row.put("assignment_id", Ids.bytes(assignment));
        row.put("record_sha256", SubmissionJson.sha(event)); row.put("payload", SubmissionJson.canonical(event));
        return row;
    }
}
