package com.acrylic.outbound.inventory;

import java.nio.file.Path;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Nonempty upgrade with an actual FORMAL stop receipt, not fabricated historical signatures. */
public final class HandoverTemporaryMigrationChecks {
    private HandoverTemporaryMigrationChecks() { }

    public static void verify(WebApplicationContext app, Path attempt, Runnable migrate) throws Exception {
        var source = new HandoverStopFixture(app, attempt.resolve("formal-before-temp-migration"));
        byte[] original = source.post(source.command, source.r.f.tokens, 200, null);
        var facts = new HandoverStopFacts(source, source.jdbc); var before = facts.snapshot();
        migrate.run();
        facts.unchanged(before);
        assertThat(source.post(source.command, source.r.f.tokens, 200, null)).isEqualTo(original);
        assertThatThrownBy(() -> source.jdbc.update("UPDATE handover_case SET source_kind='UNKNOWN' WHERE id=UUID_TO_BIN(?)", source.kase.toString()))
            .hasMessageContaining("ck_handover_source_kind");
        facts.unchanged(before);
    }
}
