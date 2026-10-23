package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.recovery.RecoverySuccessorRuntimeSupply;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Real UUID change with a full bound nonempty restore; no immutable-row rewrites or P5 promotion. */
@EnabledIfEnvironmentVariable(named = "E2A_COPY_VERIFY", matches = "true")
class E2aSuccessorCrossInstanceIT {
    @Test void retainedBindingOnAnotherPhysicalServerRequiresThenAcceptsAppendOnlySuccessor() throws Exception {
        var profile = new E2aCrossSourceProfile(); profile.connect();
        var destination = E2aArchiveRestoreTarget.crossInstance(profile);
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        Files.createFile(destination.attempt.resolve("test-started.marker"));
        try (var pool = E2aPointInTimeCheckpoint.pool(profile, profile.url)) {
            verify(profile, new JdbcTemplate(pool), destination);
        }
        for (Path home : List.of(profile.attempt, destination.attempt))
            Files.writeString(home.resolve("test-completed.marker"),
                "Bound nonempty cross-instance restore and append-only successor admission; synthetic control, no P5/API",
                StandardOpenOption.CREATE_NEW);
    }

    private static void verify(E2aCrossSourceProfile profile, JdbcTemplate source,
            E2aArchiveRestoreTarget destination) throws Exception {
        profile.migrate(source, "34"); E2aV34Seed.seed(source, profile.attempt.resolve("storage"));
        profile.migrate(source); E2aCrossSourceHttpSetup.signing(profile.attempt);
        var copier = new E2aArchiveSnapshotRestore(profile, source, destination);
        var roots = List.of(profile.attempt.resolve("data"), profile.attempt.resolve("storage"),
            destination.attempt.resolve("data"), copier.restored.resolve("storage"));
        var supply = new RecoverySuccessorRuntimeSupply(source, profile.attempt, roots);
        var target = copier.restore(); destination.requireIdentity(target);
        triggers(profile, source, target);
        var before = copier.snapshot(target, copier.restored.resolve("storage"));
        E2aRestoredClosedAdmissionIT.save(profile, "restored-before.json", before);
        identities(profile, source, target);
        var refused = supply.admission(target);
        assertThat(refused.admitted()).isFalse(); assertThat(refused.gaps()).contains("DATABASE_SERVER_UUID");
        E2aRestoredClosedAdmissionIT.save(profile, "copied-before-admission.json", refused);
        assertThat(copier.snapshot(target, copier.restored.resolve("storage"))).isEqualTo(before);
        supply.register(target, profile.attempt);
        var after = copier.snapshot(target, copier.restored.resolve("storage"));
        E2aSuccessorCompositionConservation.registration(before, after);
        E2aRestoredClosedAdmissionIT.save(profile, "restored-admitted.json", after);
        var denied = supply.admission(source);
        assertThat(denied.admitted()).isFalse(); assertThat(denied.gaps()).containsExactly("DATABASE_EVIDENCE_UNAVAILABLE");
        E2aRestoredClosedAdmissionIT.save(profile, "source-after-admission.json", denied);
        var sourceAfter = copier.snapshot(source, profile.attempt.resolve("storage"));
        assertThat(sourceAfter).isEqualTo(before);
        E2aRestoredClosedAdmissionIT.save(profile, "source-final.json", sourceAfter);
        E2aRestoredClosedAdmissionIT.save(profile, "authority-originals.json", supply.originals());
    }

    private static void identities(E2aCrossSourceProfile profile, JdbcTemplate source, JdbcTemplate target) throws Exception {
        String sql = "SELECT @@server_uuid AS uuid,DATABASE() AS schemaName,@@port AS port,@@datadir AS datadir,CURRENT_USER() AS userName";
        var original = source.queryForMap(sql); var copied = target.queryForMap(sql);
        assertThat(copied.get("uuid")).isNotEqualTo(original.get("uuid"));
        assertThat(copied.get("port")).isNotEqualTo(original.get("port"));
        assertThat(copied.get("datadir")).isNotEqualTo(original.get("datadir"));
        E2aRestoredClosedAdmissionIT.save(profile, "source-identity.json", original);
        E2aRestoredClosedAdmissionIT.save(profile, "target-identity.json", copied);
    }

    private static void triggers(E2aCrossSourceProfile profile, JdbcTemplate source, JdbcTemplate target) throws Exception {
        String sql = "SELECT TRIGGER_NAME,EVENT_MANIPULATION,EVENT_OBJECT_TABLE,ACTION_ORDER,ACTION_CONDITION,"
            + "ACTION_STATEMENT,ACTION_ORIENTATION,ACTION_TIMING,SQL_MODE,DEFINER,CHARACTER_SET_CLIENT,"
            + "COLLATION_CONNECTION,DATABASE_COLLATION FROM information_schema.TRIGGERS "
            + "WHERE TRIGGER_SCHEMA=DATABASE() ORDER BY TRIGGER_NAME";
        var original = source.queryForList(sql); var copied = target.queryForList(sql);
        assertThat(original).isNotEmpty(); assertThat(copied).isEqualTo(original);
        E2aRestoredClosedAdmissionIT.save(profile, "source-trigger-facts.json", original);
        E2aRestoredClosedAdmissionIT.save(profile, "target-trigger-facts.json", copied);
    }
}
