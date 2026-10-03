package com.acrylic.outbound.correction;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CorrectionOccupancyRestorationTest {
    @Test void nonReopeningCorrectionDoesNotReadOrMutateOccupancy() {
        var jdbc = mock(JdbcTemplate.class);
        var restored = new CorrectionOccupancyRestoration(jdbc).restore(write("WEIGHT_CORRECTION"), List.of(line()));

        assertThat(restored).isEmpty();
        verifyNoInteractions(jdbc);
    }

    private CorrectionCommand.Write write(String type) {
        var body = SubmissionJson.object().put("commandVersion", 1).put("commandType", "WITHDRAW_CONFIRM")
            .put("commandId", UUID.randomUUID().toString()).put("warehouseId", UUID.randomUUID().toString());
        var actor = SubmissionJson.object().put("principalId", UUID.randomUUID().toString());
        var anchor = SubmissionJson.object().put("type", type);
        return new CorrectionCommand.Write(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), Instant.parse("2026-10-03T00:00:00Z"),
            new CorrectionCommand.Input(SubmissionJson.canonical(body).getBytes(StandardCharsets.UTF_8)), anchor, actor);
    }

    private Map<String, Object> line() {
        return Map.of("line_id", UUID.randomUUID(), "dispatch_id", UUID.randomUUID());
    }
}
