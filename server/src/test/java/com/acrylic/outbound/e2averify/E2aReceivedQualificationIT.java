package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.recovery.RecoveryReceivedQualificationChecks;
import com.acrylic.outbound.recovery.RecoveryReceivedCheckpointTargetChecks;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aReceivedQualificationIT {
    @Test @EnabledIfEnvironmentVariable(named="E2A_RECEIVED_PHASE",matches="target")
    void issuesScopedTargetEpochQualificationFromOriginalSource() throws Exception {
        var profile=new E2aCrossSourceProfile(); var jdbc=profile.connect(); Files.createFile(profile.attempt.resolve("test-started.marker"));
        Path original=Path.of(System.getenv("E2A_RECEIVED_SOURCE"));
        RecoveryReceivedCheckpointTargetChecks.verify(jdbc,profile.attempt,original);
        var snapshots=new E2aMigrationSnapshot(jdbc,profile.attempt,profile.attempt.resolve("storage"));
        var before=snapshots.capture(); profile.migrate(jdbc); var after=snapshots.capture();
        before.path("tables").fields().forEachRemaining(e->assertThat(after.path("tables").path(e.getKey())).as(e.getKey()).isEqualTo(e.getValue()));
        assertThat(after.path("files")).isEqualTo(before.path("files"));
        assertThat(after.path("tables").path("recovery_recipient_qualification").path("rows").size()).isZero();
        Files.writeString(profile.attempt.resolve("qualification-migration-before.json"),before.toPrettyString());
        Files.writeString(profile.attempt.resolve("qualification-migration-after.json"),after.toPrettyString());
        for(String file:new String[]{"server-private.der","server-public.der"})
            Files.copy(original.resolve("private").resolve(file),profile.attempt.resolve("private").resolve(file));
        try(var app=E2aCrossSourceHttpIT.start(profile,"--outbound.handover.commands.enabled=true",
                "--outbound.handover.fault.enabled=true","--server.servlet.session.cookie.secure=false")) {
            RecoveryReceivedQualificationChecks.verify((WebApplicationContext)app,profile.attempt,original);
        }
        E2aQualificationReplayMain.launch(profile.attempt);
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"New scoped target-epoch qualification; original source preserved; first round and scanning remain closed");
    }
}
