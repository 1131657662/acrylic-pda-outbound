package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.exports.*;
import java.time.Instant;
import java.util.*;
import org.springframework.context.ConfigurableApplicationContext;
import static com.acrylic.outbound.exports.ExportModels.*;
import static org.assertj.core.api.Assertions.*;

final class JobFenceExportChecks {
    static void verify(ConfigurableApplicationContext context) throws Exception {
        var f = new JobFenceFixture(context); var json = context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class);
        var queries = context.getBean(ExportJobQueries.class);
        var scope = new Scope(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var payload = new Payload(scope, Format.XLSX, "ORIGINAL_CONFIRMED", f.state.current().epoch(),
            "a".repeat(64), "synthetic-snapshot", "synthetic-order", 1, "2026-09-20", Format.XLSX.template(),
            Instant.now().plusSeconds(3600), null, null, 1, null);
        for (int i = 0; i < 5; i++) {
            f.jdbc.update("""
                INSERT INTO background_job(id,job_type,dedupe_key,status,payload,max_attempts,available_at,created_at,updated_at)
                VALUES(?,? ,?,'PENDING',?,3,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
                """, Ids.bytes(UUID.randomUUID()), TYPE, "quarantine:" + UUID.randomUUID(), json.writeValueAsString(payload));
        }
        var isolated = queries.list(scope, new ExportJobQueries.Filter(null, null, "INVALIDATED"));
        assertThat(isolated.items()).hasSize(5).allMatch(item -> !item.downloadable());
        assertThat(queries.list(scope, new ExportJobQueries.Filter(null, null, "PENDING")).items()).isEmpty();
        assertThatCode(() -> queries.checkCapacity(scope.actor())).doesNotThrowAnyException();
    }
}
