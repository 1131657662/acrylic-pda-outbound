package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.recovery.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aReceivedDownloadHttpIT {
    @Test @EnabledIfEnvironmentVariable(named="E2A_RECEIVED_PHASE",matches="target")
    void downloadsOriginalThroughConfiguredProductRoutes() throws Exception {
        var profile=new E2aCrossSourceProfile(); var jdbc=profile.connect(); Files.createFile(profile.attempt.resolve("test-started.marker"));
        Path original=Path.of(System.getenv("E2A_RECEIVED_SOURCE")); RecoveryReceivedCheckpointTargetChecks.verify(jdbc,profile.attempt,original);
        var snapshots=new E2aMigrationSnapshot(jdbc,profile.attempt,profile.attempt.resolve("storage")); var before=snapshots.capture();
        profile.migrate(jdbc); var after=snapshots.capture();
        before.path("tables").fields().forEachRemaining(e->assertThat(after.path("tables").path(e.getKey())).as(e.getKey()).isEqualTo(e.getValue()));
        assertThat(after.path("files")).isEqualTo(before.path("files"));
        for(String file:new String[]{"server-private.der","server-public.der"})
            Files.copy(original.resolve("private").resolve(file),profile.attempt.resolve("private").resolve(file));
        try(var app=E2aCrossSourceHttpIT.start(profile,"--outbound.handover.commands.enabled=true",
                "--outbound.handover.fault.enabled=true","--server.servlet.session.cookie.secure=false")) {
            RecoveryReceivedDownloadChecks.verify((WebApplicationContext)app,profile.attempt,original);
            RecoveryReceivedDownloadHttpInput.save(profile.attempt,original);
        }
        var options=java.util.stream.Stream.concat(java.util.stream.Stream.of("--outbound.handover.commands.enabled=true",
            "--outbound.handover.fault.enabled=true","--server.servlet.session.cookie.secure=false"),
            java.util.Arrays.stream(RecoveryReceivedDownloadHttpInput.options(profile.attempt))).toArray(String[]::new);
        try(var app=E2aCrossSourceHttpIT.start(profile,options)) { RecoveryReceivedDownloadHttpChecks.verify((WebApplicationContext)app,profile.attempt,false); }
        E2aSessionReplayMain.launch(profile.attempt);
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Configured product HTTP original download and new JVM original token");
    }
}
