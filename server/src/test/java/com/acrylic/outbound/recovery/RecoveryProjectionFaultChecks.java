package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static com.acrylic.outbound.recovery.RecoveryProjectionChecks.*;

/** Faults use preserved copies in preallocated schemas; no original history is deleted or repaired. */
final class RecoveryProjectionFaultChecks {
    static void atInitial(JdbcTemplate jdbc, RecoveryTransactionFixture f, RecoveryProjectionDatabases databases, long baseline) throws Exception {
        for (String suffix : List.of("copy", "instance")) {
            var copy = databases.copy(suffix);
            assertThatThrownBy(() -> projection(copy, f, baseline).project()).hasMessage("CONTROL_DATABASE_INSTANCE_MISMATCH");
        }
        var conflict = databases.copy("conflict");
        assertThatThrownBy(() -> projection(conflict, f, baseline).project()).isInstanceOf(RecoveryControlException.class);
        var missing = databases.copy("missing");
        assertThatThrownBy(() -> projection(missing, f, baseline).project()).hasMessage("CONTROL_DATABASE_PROJECTION_MISMATCH");
        var wrong = new RecoveryTransactionFixture(f.root.resolveSibling("projection-other-installation"));
        assertThatThrownBy(() -> projection(jdbc, wrong, baseline).project()).hasMessage("CONTROL_DATABASE_INSTALLATION_MISMATCH");
        initialRollback(f, databases.empty("rollback"), baseline);
        overflow(f, databases.empty("overflow"));
        skipped(f, databases.empty("skip"), baseline);
    }

    static void atFinal(JdbcTemplate jdbc, RecoveryTransactionFixture f, RecoveryProjectionDatabases databases, long baseline) throws Exception {
        var gap = databases.copy("gap");
        assertThatThrownBy(() -> projection(gap, f, baseline).project()).hasMessage("CONTROL_DATABASE_CHAIN_CONFLICT");
        var mapping = databases.copy("mapping");
        assertThatThrownBy(() -> projection(mapping, f, baseline).project()).hasMessage("CONTROL_DATABASE_EPOCH_CONFLICT");
        String before = jdbc.queryForObject("SELECT service_mode FROM server_state", String.class);
        jdbc.update("UPDATE server_state SET service_mode='NORMAL'");
        try { assertThatThrownBy(() -> projection(jdbc, f, baseline).project()).hasMessage("CONTROL_DATABASE_PROJECTION_MISMATCH"); }
        finally { jdbc.update("UPDATE server_state SET service_mode=?", before); }
        byte[] pointer = Files.readAllBytes(f.location.root().resolve("current.json"));
        Files.write(f.root.resolve("retained-final-pointer.json"), pointer);
        try {
            Files.write(f.location.root().resolve("current.json"), Files.readAllBytes(f.checkpoint));
            assertThatThrownBy(() -> projection(jdbc, f, baseline).project()).hasMessage("CONTROL_COMMAND_INCOMPLETE");
        } finally { Files.write(f.location.root().resolve("current.json"), pointer); }
        wholeAuthorityRollback(jdbc, f, baseline);
        assertThat(count(jdbc, "recovery_control_projection")).isEqualTo(4);
    }

    private static void wholeAuthorityRollback(JdbcTemplate jdbc, RecoveryTransactionFixture f, long baseline) throws Exception {
        var preserved = Files.createDirectory(f.root.resolve("retained-authority-rollback"));
        var history = f.location.root().resolve("history");
        var intents = f.location.root().resolve("intents");
        byte[] pointer = Files.readAllBytes(f.location.root().resolve("current.json"));
        byte[] admission = Files.readAllBytes(f.location.root().resolve("admission.json"));
        try {
            retainSuffix(history, preserved.resolve("history")); retainSuffix(intents, preserved.resolve("intents"));
            Files.write(f.location.root().resolve("current.json"), Files.readAllBytes(f.checkpoint));
            RecoveryControlFixture.write(f.location.root().resolve("admission.json"), RecoveryControlFixture.JSON.createObjectNode()
                .put("formatVersion", 1).put("installationId", f.location.installation().toString())
                .put("targetRevision", "1").put("intentSha256", RecoveryControlFormat.ZERO_HASH));
            assertThat(new RecoveryControlReader(f.location).read(f.checkpoint).revision()).isEqualTo("1");
            assertThatThrownBy(() -> projection(jdbc, f, baseline).project()).hasMessage("CONTROL_DATABASE_CHAIN_CONFLICT");
        } finally {
            restoreSuffix(history, preserved.resolve("history")); restoreSuffix(intents, preserved.resolve("intents"));
            Files.write(f.location.root().resolve("current.json"), pointer);
            Files.write(f.location.root().resolve("admission.json"), admission);
        }
    }

    private static void retainSuffix(Path directory, Path destination) throws Exception {
        Files.createDirectory(destination);
        for (long revision = 2; revision <= 4; revision++) {
            var file = directory.resolve(String.format(Locale.ROOT, "%018d.json", revision));
            Files.move(file, destination.resolve(file.getFileName()));
        }
    }

    private static void restoreSuffix(Path directory, Path retained) throws Exception {
        if (!Files.isDirectory(retained)) return;
        try (var stream = Files.list(retained)) {
            for (Path file : stream.toList()) Files.copy(file, directory.resolve(file.getFileName()));
        }
    }

    private static void initialRollback(RecoveryTransactionFixture f, JdbcTemplate jdbc, long baseline) {
        jdbc.execute("CREATE TRIGGER projection_test_fail BEFORE INSERT ON recovery_control_projection FOR EACH ROW "
            + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected projection insert failure'");
        assertThatThrownBy(() -> projection(jdbc, f, baseline).project()).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertEmpty(jdbc);
        assertThat(jdbc.queryForObject("SELECT recovery_epoch FROM server_state", Long.class)).isEqualTo(baseline);
    }

    private static void overflow(RecoveryTransactionFixture original, JdbcTemplate jdbc) throws Exception {
        var f = new RecoveryTransactionFixture(original.root.resolveSibling("projection-overflow"));
        jdbc.update("UPDATE server_state SET recovery_epoch=?", Long.MAX_VALUE);
        projection(jdbc, f, Long.MAX_VALUE).project(); f.advancePhase("RECOVERING");
        assertThatThrownBy(() -> projection(jdbc, f, Long.MAX_VALUE).project()).isInstanceOf(ArithmeticException.class);
        assertThat(count(jdbc, "recovery_control_epoch")).isEqualTo(1);
        assertThat(count(jdbc, "recovery_control_projection")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT recovery_epoch FROM server_state", Long.class)).isEqualTo(Long.MAX_VALUE);
    }

    private static void skipped(RecoveryTransactionFixture original, JdbcTemplate jdbc, long baseline) throws Exception {
        var f = new RecoveryTransactionFixture(original.root.resolveSibling("projection-skip"));
        projection(jdbc, f, baseline).project();
        f.advancePhase("RECOVERING"); f.advancePhase("VERIFYING");
        assertThatThrownBy(() -> projection(jdbc, f, baseline).project()).hasMessage("CONTROL_DATABASE_CHAIN_CONFLICT");
        assertThat(count(jdbc, "recovery_control_epoch")).isEqualTo(1);
        assertThat(count(jdbc, "recovery_control_projection")).isEqualTo(1);
    }
}
