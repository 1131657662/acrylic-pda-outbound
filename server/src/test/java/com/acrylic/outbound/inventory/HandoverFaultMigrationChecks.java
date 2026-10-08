package com.acrylic.outbound.inventory;

import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Genuine nonempty TEMP origin, archive, request and abort survive the fault migration byte-for-byte. */
final class HandoverFaultMigrationChecks {
    static void verify(HandoverTemporaryFaultFenceChecks f, Path attempt, Runnable migrate) throws Exception {
        var snapshots = new E2aMigrationSnapshot(f.source.jdbc, attempt, attempt.resolve("storage"));
        var before = snapshots.capture();
        Files.writeString(f.directory.resolve("migration-before.json"), before.toString());
        migrate.run(); var after = snapshots.capture();
        Files.writeString(f.directory.resolve("migration-after.json"), after.toString());
        before.path("tables").fields().forEachRemaining(row -> assertThat(after.path("tables").path(row.getKey()))
            .as(row.getKey()).isEqualTo(row.getValue()));
        assertThat(after.path("files")).isEqualTo(before.path("files"));
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM handover_fault_fence", Long.class)).isZero();
        Files.writeString(f.directory.resolve("migration-preserved.marker"),
            "All existing table rows, original origin/E2A/request/abort bytes and private files unchanged; empty fault table added.\n");
    }
}
