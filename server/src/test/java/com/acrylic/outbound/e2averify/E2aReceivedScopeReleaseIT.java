package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.recovery.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aReceivedScopeReleaseIT {
    @Test @EnabledIfEnvironmentVariable(named="E2A_RECEIVED_PHASE",matches="target")
    void preparesScopeReleaseWithConfirmedDatabaseCommit() throws Exception {
        var profile=new E2aCrossSourceProfile(); var jdbc=profile.connect(); Files.createFile(profile.attempt.resolve("test-started.marker"));
        Path original=Path.of(System.getenv("E2A_RECEIVED_SOURCE"));
        RecoveryReceivedCheckpointTargetChecks.verify(jdbc,profile.attempt,original);
        var snapshots=new E2aMigrationSnapshot(jdbc,profile.attempt,profile.attempt.resolve("storage"));
        var restored=snapshots.capture(); profile.migrate(jdbc); var upgraded=snapshots.capture();
        restored.path("tables").fields().forEachRemaining(entry->org.assertj.core.api.Assertions.assertThat(
            upgraded.path("tables").path(entry.getKey())).as(entry.getKey()).isEqualTo(entry.getValue()));
        org.assertj.core.api.Assertions.assertThat(upgraded.path("files")).isEqualTo(restored.path("files"));
        org.assertj.core.api.Assertions.assertThat(upgraded.path("tables").path("recovery_scope_release").path("rows").isEmpty()).isTrue();
        Files.writeString(profile.attempt.resolve("scope-release-migration-before.json"),restored.toPrettyString());
        Files.writeString(profile.attempt.resolve("scope-release-migration-after.json"),upgraded.toPrettyString());
        for(String file:new String[]{"server-private.der","server-public.der"})
            Files.copy(original.resolve("private").resolve(file),profile.attempt.resolve("private").resolve(file));
        try(var app=E2aCrossSourceHttpIT.start(profile,"--outbound.handover.commands.enabled=true",
                "--outbound.handover.fault.enabled=true","--server.servlet.session.cookie.secure=false")) {
            RecoveryReceivedScopeReleaseChecks.verify((WebApplicationContext)app,profile.attempt,original);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Synthetic operations approval -> PREPARE/confirmed closed DB scope commit; external COMMIT not published");
    }
    @Test @EnabledIfEnvironmentVariable(named="E2A_RECEIVED_PHASE",matches="target")
    void replaysScopeReleaseInNewJvm() throws Exception {
        var profile=E2aCrossSourceProfile.completedScopeRelease();
        Files.createFile(profile.attempt.resolve("scope-restart-started.marker"));
        E2aScopeReleaseCrashMain.launch(profile.attempt);
        try(var app=E2aCrossSourceHttpIT.start(profile,"--outbound.handover.commands.enabled=true",
                "--outbound.handover.fault.enabled=true","--server.servlet.session.cookie.secure=false")) {
            RecoveryReceivedScopeReleaseRestart.verify((WebApplicationContext)app,profile.attempt,Path.of(System.getenv("E2A_RECEIVED_SOURCE")));
        }
        Files.writeString(profile.attempt.resolve("scope-restart-completed.marker"),"Original pins reload after child halt; both scope commit sides verified");
    }
}
