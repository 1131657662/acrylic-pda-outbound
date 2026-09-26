package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceEvidenceIntakeOpenLookupTest {
    private static final byte[] REQUEST="""
        {"operation":"OPEN","manifestId":"manifest","manifestVersion":2,"snapshotId":"snapshot",
         "source":"TEMP","sourceId":"33333333-3333-4333-8333-333333333333",
         "submissionId":"44444444-4444-4444-8444-444444444444","workRound":3}
        """.getBytes(StandardCharsets.UTF_8);
    private static final byte[] RESPONSE="{\"status\":\"RECEIVED\"}".getBytes(StandardCharsets.UTF_8);
    private final SourceEvidenceIntakeReceipts.BoundKey key=new SourceEvidenceIntakeReceipts.BoundKey(
        UUID.fromString("11111111-1111-4111-8111-111111111111"),UUID.fromString("22222222-2222-4222-8222-222222222222"),
        UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),UUID.fromString("44444444-4444-4444-8444-444444444444"),
        3,UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"),UUID.fromString("33333333-3333-4333-8333-333333333333"));
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final SourceEvidenceIntakeReceipts receipts=new SourceEvidenceIntakeReceipts(jdbc);

    @Test void readsOriginalOpenOnlyFromBoundSource() {
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(row()));

        var open=receipts.openBound(key);

        assertThat(open.manifestId()).isEqualTo("manifest");
        assertThat(open.manifestVersion()).isEqualTo(2);
        assertThat(open.snapshotId()).isEqualTo("snapshot");
        verify(jdbc).queryForList(org.mockito.ArgumentMatchers.argThat(sql->sql.contains("original_key=?")),
            org.mockito.ArgumentMatchers.eq(Ids.bytes(key.principalId())),
            org.mockito.ArgumentMatchers.eq(E2aProtocol.ROUTE_SCOPE),org.mockito.ArgumentMatchers.eq(Ids.bytes(key.originalKey())));
    }

    @Test void otherSubmissionCannotReadTheOpenBytes() {
        var row=row();row.put("submission_id",Ids.bytes(UUID.randomUUID()));
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(row));

        assertThatThrownBy(() -> receipts.openBound(key))
            .isInstanceOfSatisfying(com.acrylic.outbound.common.BusinessException.class,
                error->assertThat(error.code()).isEqualTo("E2A_OPEN_NOT_FOUND"));
    }

    @Test void damagedOriginalResponseFailsClosed() {
        var row=row();row.put("original_response_sha256","0".repeat(64));
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(row));

        assertThatThrownBy(() -> receipts.openBound(key))
            .isInstanceOfSatisfying(com.acrylic.outbound.common.BusinessException.class,
                error->assertThat(error.code()).isEqualTo("E2A_OPEN_INTEGRITY_FAILURE"));
    }

    @Test void missingOpenCannotBeInvented() {
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());

        assertThatThrownBy(() -> receipts.openBound(key))
            .isInstanceOfSatisfying(com.acrylic.outbound.common.BusinessException.class,
                error->assertThat(error.code()).isEqualTo("E2A_OPEN_NOT_FOUND"));
    }

    @Test void lockedLookupUsesForUpdate() {
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(row()));

        assertThat(receipts.openBoundLocked(key).manifestId()).isEqualTo("manifest");

        verify(jdbc).queryForList(org.mockito.ArgumentMatchers.argThat(sql->sql.trim().endsWith("FOR UPDATE")),
            org.mockito.ArgumentMatchers.eq(Ids.bytes(key.principalId())),
            org.mockito.ArgumentMatchers.eq(E2aProtocol.ROUTE_SCOPE),org.mockito.ArgumentMatchers.eq(Ids.bytes(key.originalKey())));
    }

    private Map<String,Object> row() {
        var row=new HashMap<String,Object>();
        byte[] canonical=E2aProtocol.canonical(REQUEST);
        row.put("receipt_version",1);row.put("canonical_request_bytes",canonical);
        row.put("request_sha256",E2aProtocol.intakeSha256(canonical));
        row.put("original_response_bytes",RESPONSE);row.put("original_response_sha256",ImportFiles.sha256(RESPONSE));
        row.put("warehouse_id",Ids.bytes(key.warehouseId()));row.put("submission_id",Ids.bytes(key.submissionId()));
        row.put("work_round",3L);row.put("creator_device_id",Ids.bytes(key.creatorDeviceId()));
        row.put("temporary_authorization_id",Ids.bytes(key.authorizationId()));
        row.put("original_operator_id",Ids.bytes(key.principalId()));
        row.put("operation_kind","OPEN");row.put("receipt_status","RECEIVED");
        return row;
    }
}
