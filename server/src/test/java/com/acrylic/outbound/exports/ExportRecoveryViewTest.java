package com.acrylic.outbound.exports;

import com.acrylic.outbound.common.ServerState;
import com.acrylic.outbound.jobs.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static com.acrylic.outbound.exports.ExportModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExportRecoveryViewTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final ServerState state = mock(ServerState.class);

    @Test void unknownAndOldJobsAreInvalidatedEvenWhenPayloadClaimsCurrentEpoch() throws Exception {
        var queries = new ExportJobQueries(null, null, json, state);
        when(state.current()).thenReturn(new ServerState.State("7", "NORMAL"));
        for (String epoch : new String[]{null, "6"}) {
            var view = queries.view(job(epoch));
            assertThat(view.status()).isEqualTo("INVALIDATED");
            assertThat(view.downloadable()).isFalse();
        }
    }

    @Test void knownCurrentCompletedJobRemainsDownloadable() throws Exception {
        var queries = new ExportJobQueries(null, null, json, state);
        when(state.current()).thenReturn(new ServerState.State("7", "NORMAL"));
        assertThat(queries.view(job("7")).downloadable()).isTrue();
        assertThat(queries.view(job("7")).status()).isEqualTo("SUCCEEDED");
    }

    private BackgroundJob job(String epoch) throws Exception {
        var scope = new Scope(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var payload = new Payload(scope, Format.XLSX, "ORIGINAL_CONFIRMED", "7", "a".repeat(64), "snapshot",
            "FH-synthetic", 1, "2026-09-20", Format.XLSX.template(), Instant.now().plusSeconds(3600),
            new Artifact(UUID.randomUUID(), "b".repeat(64), 100), null, 1, null);
        return new BackgroundJob(UUID.randomUUID(), TYPE, "synthetic", BackgroundJob.Status.SUCCEEDED,
            json.writeValueAsString(payload), 1, 3, Instant.EPOCH, null, null, null, null,
            Instant.EPOCH, Instant.EPOCH, epoch);
    }
}
