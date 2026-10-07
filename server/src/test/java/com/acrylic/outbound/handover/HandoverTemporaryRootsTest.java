package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.temporary.evidence.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HandoverTemporaryRootsTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void unavailableOpenRootIsAnExplicitConflict(boolean missing) {
        var jdbc = mock(JdbcTemplate.class); var opens = mock(SourceEvidenceIntakeReceipts.class);
        var keys = mock(SourceEvidenceIntakeDeviceKeys.class);
        var request = object().put("openOriginalKey", UUID.randomUUID().toString());
        when(jdbc.queryForList(anyString(), any(Object[].class)))
            .thenReturn(List.of(Map.of("canonical_request_bytes", HandoverReceiptCodec.bytes(request))));
        if (missing) when(opens.openBound(any())).thenThrow(new BusinessException(404, "E2A_OPEN_NOT_FOUND", "missing"));
        var roots = new HandoverTemporaryRoots(jdbc, opens, keys, null, null);
        assertThatThrownBy(() -> roots.require(command(), origin())).isInstanceOfSatisfying(BusinessException.class,
            error -> { assertThat(error.status()).isEqualTo(409); assertThat(error.code()).isEqualTo("HANDOVER_EVIDENCE_CHANGED"); });
    }

    private HandoverStopCommand command() {
        var body = object().put("sourceKind", "TEMP").put("commandId", UUID.randomUUID().toString()).put("expectedCaseVersion", "1")
            .put("sourceEvidenceSetId", UUID.randomUUID().toString()).put("verificationId", UUID.randomUUID().toString())
            .put("setSha256", "a".repeat(64)).put("captureHighWatermark", "2").put("auditThroughSeq", "8");
        return HandoverStopCommand.parse(UUID.randomUUID(), "1", HandoverReceiptCodec.bytes(body));
    }

    private HandoverStopStore.Origin origin() {
        var device = UUID.randomUUID();
        return new HandoverStopStore.Origin(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            null, device, UUID.randomUUID(), "1", 1,
            new HandoverStopStore.Temporary(UUID.randomUUID(), device, UUID.randomUUID()));
    }
}
