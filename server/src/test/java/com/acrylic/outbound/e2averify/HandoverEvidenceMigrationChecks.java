package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

/** Upgrade genuine initial-device E2A receipts before accepting a different freeze device. */
final class HandoverEvidenceMigrationChecks {
    private HandoverEvidenceMigrationChecks() { }
    static void verify(E2aCrossSourceProfile profile,JdbcTemplate jdbc) throws Exception {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM source_evidence_intake_receipt",Long.class)).isPositive();
        var snapshots=new E2aMigrationSnapshot(jdbc,profile.attempt,profile.attempt.resolve("storage"));
        var before=snapshots.capture();Files.writeString(profile.attempt.resolve("e2a-origin-before.json"),canonical(before));
        profile.migrate(jdbc);var after=snapshots.capture();
        Files.writeString(profile.attempt.resolve("e2a-origin-after.json"),canonical(after));
        assertThat(after.path("tables")).isEqualTo(before.path("tables"));
        assertThat(after.path("files")).isEqualTo(before.path("files"));
        rejectChangedOrigin(jdbc);var finalFacts=snapshots.capture();
        assertThat(finalFacts.path("tables")).isEqualTo(after.path("tables"));
        assertThat(finalFacts.path("files")).isEqualTo(after.path("files"));
        Files.writeString(profile.attempt.resolve("e2a-origin-migration.marker"),
            "Nonempty genuine source receipts: all table rows/private bytes retained; wrong freeze device and scope client rejected by FK.\n");
    }
    private static void rejectChangedOrigin(JdbcTemplate jdbc) {
        assertThatThrownBy(()->jdbc.update("UPDATE source_evidence_intake_receipt SET creator_device_id=UUID_TO_BIN(?) LIMIT 1",
            java.util.UUID.randomUUID().toString())).hasMessageContaining("source evidence intake receipts are immutable");
        assertThatThrownBy(()->insertWrongOrigin(jdbc,"creator_device_id")).hasMessageContaining("foreign key constraint fails");
        assertThatThrownBy(()->insertWrongOrigin(jdbc,"client_order_id")).hasMessageContaining("fk_e2a_receipt_scope_client");
    }
    private static void insertWrongOrigin(JdbcTemplate jdbc,String changed) {
        var columns=jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
            +"AND TABLE_NAME='source_evidence_intake_receipt' ORDER BY ORDINAL_POSITION",String.class);
        var values=columns.stream().map(name->java.util.Set.of("id","original_key",changed).contains(name)
            ?"UUID_TO_BIN(UUID())":E2aSnapshotRows.identifier(name)).toList();
        String names=String.join(",",columns.stream().map(E2aSnapshotRows::identifier).toList());
        jdbc.update("INSERT INTO source_evidence_intake_receipt ("+names+") SELECT "+String.join(",",values)
            +" FROM source_evidence_intake_receipt LIMIT 1");
    }
}
