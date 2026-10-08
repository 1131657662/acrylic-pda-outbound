package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Body identity validation with real canonical files; complete E2A roots are exercised separately over HTTP. */
class HandoverTemporaryPayloadTest {
    @TempDir Path directory;
    private final UUID device = UUID.randomUUID();
    private final HandoverStopStore.Origin origin = new HandoverStopStore.Origin(UUID.randomUUID(), UUID.randomUUID(),
        UUID.randomUUID(), UUID.randomUUID(), null, device, UUID.randomUUID(), "1", 1,
        new HandoverStopStore.Temporary(UUID.randomUUID(), device, UUID.randomUUID()));

    @Test void unchangedCanonicalFirstRoundRetainsItsSubmissionIdentity() {
        var body = body();
        assertThat(reader(body).read(roots(), origin)).isEqualTo(body);
    }

    @Test void validFileWithWrongTopLevelSubmissionIsRejected() {
        var body = body().put("submissionId", UUID.randomUUID().toString());
        assertThatThrownBy(() -> reader(body).read(roots(), origin)).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("HANDOVER_EVIDENCE_CHANGED"));
    }

    private HandoverTemporaryPayload reader(ObjectNode body) {
        var files = new SubmissionFiles(directory.toString());
        String hash = files.store(HandoverReceiptCodec.bytes(body));
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), any(Object.class))).thenReturn(List.of(hash));
        return new HandoverTemporaryPayload(jdbc, files, null, null, null, null, null);
    }

    private HandoverTemporaryRoots.Verified roots() {
        return new HandoverTemporaryRoots.Verified(null, object().put("pageCount", 0));
    }

    private ObjectNode body() {
        var body = object().put("submissionId", origin.submission().toString())
            .put("authorizationId", origin.temporary().authorization().toString()).put("freezeDeviceId", device.toString())
            .put("workRound", "1").put("sourceRecoveryEpoch", "1").putNull("assignmentId").putNull("continuationId");
        body.putObject("source").put("kind", "TEMP").put("creatorDeviceId", device.toString())
            .put("clientOrderId", origin.temporary().client().toString());
        body.putArray("captures"); body.putArray("scans");
        return body;
    }
}
