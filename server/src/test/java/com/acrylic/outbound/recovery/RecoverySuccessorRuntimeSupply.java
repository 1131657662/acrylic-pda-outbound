package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Explicit test-only bridge from retained first-round originals to real successor product JVMs. */
public final class RecoverySuccessorRuntimeSupply {
    private final RecoverySuccessorDatabaseFixture fixture;
    private final RecoveryDatabaseSource.Pins source;
    private RecoveryRestoredAdmissionP5 p5;

    public RecoverySuccessorRuntimeSupply(JdbcTemplate jdbc, Path attempt) throws Exception {
        fixture = new RecoverySuccessorDatabaseFixture(jdbc, attempt.resolve("successor-control"),
            List.of(attempt.resolve("data"), attempt.resolve("restored/storage")));
        assertThat(fixture.runtimeAdmitted(jdbc)).isTrue();
        source = fixture.pins(jdbc);
        RecoverySuccessorAdmissionChecks.save(attempt.resolve("source-pins.json"), source);
    }

    public void register(JdbcTemplate target, Path attempt) throws Exception {
        assertThat(fixture.runtimeAdmitted(target)).isFalse();
        fixture.successor(target, source, Map.of()); fixture.project(target);
        for (String phase : List.of("RECOVERING", "VERIFYING", "RELEASE_READY")) {
            fixture.advance(phase); fixture.project(target);
        }
        fixture.writeNormal(target);
        assertThat(fixture.admit(target).outcome()).isEqualTo("REGISTERED");
        assertThat(fixture.admit(target).outcome()).isEqualTo("ALREADY_REGISTERED");
        var admitted = fixture.runtimeResult(target);
        assertThat(admitted.admitted()).as(admitted.gaps().toString()).isTrue();
        assertThat(admitted.ordinaryWritesAllowed()).isFalse();
        assertThat(admitted.localDeletionAllowed()).isFalse();
        RecoverySuccessorAdmissionChecks.save(attempt.resolve("successor-admitted.json"), admitted);
    }

    public RecoveryRestoredAdmissionP5 enableP5(JdbcTemplate target, Path attempt) throws Exception {
        assertThat(p5).isNull();
        p5 = new RecoveryRestoredAdmissionP5(fixture.location(), attempt);
        assertThat(fixture.runtimeAdmitted(target)).isTrue();
        return p5;
    }

    public String[] options() {
        var location = fixture.location();
        var result = new ArrayList<>(List.of("--outbound.recovery.admission.enabled=true",
            "--outbound.recovery.baseline=" + fixture.baseline(),
            "--outbound.recovery.root=" + location.root(),
            "--outbound.recovery.installation-id=" + location.installation(),
            "--outbound.recovery.restore-roots[0]=" + location.restoreRoots().get(0),
            "--outbound.recovery.restore-roots[1]=" + location.restoreRoots().get(1),
            "--outbound.recovery.checkpoint=" + fixture.checkpoint()));
        if (p5 != null) result.addAll(List.of(p5.options()));
        return result.toArray(String[]::new);
    }

    public Map<String, String> originals() throws Exception {
        return ClosedAdmissionIdentityObservation.files(fixture.root());
    }
}
