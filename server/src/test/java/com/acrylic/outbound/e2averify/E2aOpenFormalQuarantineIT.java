package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.*;
import java.nio.file.Files;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aOpenFormalQuarantineIT {
    @Test void pendingApplicationHasAnOriginalPhysicalProofWithoutFinalDispatch() throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));profile.migrate(jdbc,"90");
        var originals=new CorrectionAccountingMigrationOriginals(jdbc,profile.attempt);var before=originals.snapshot("open-formal-before");
        var migration=Flyway.configure().dataSource(jdbc.getDataSource()).load();migration.migrate();migration.validate();migration.migrate();
        assertThat(originals.snapshot("open-formal-after")).isEqualTo(before);
        var key=E2aCrossSourceProfile.ROOT.resolve(".local/it-keys");
        Files.copy(key.resolve("server-public.der"),profile.attempt.resolve("private/server-public.der"));
        Files.copy(key.resolve("server-private.der"),profile.attempt.resolve("private/server-private.der"));
        try(var context=E2aCrossSourceHttpIT.start(profile,"--server.servlet.session.cookie.secure=false",
            "--outbound.corrections.commands.enabled=true","--outbound.corrections.attachments.enabled=true")) {
            var source=new FormalQuarantineHttpSource((WebApplicationContext)context,profile.attempt,jdbc);source.originalsThroughHttp(true);
            if(!"true".equals(System.getenv("OPEN_FORMAL_WEB_PREPARE"))) {
                var checks=new OpenFormalQuarantineHttpChecks(source);
                checks.rejectsUnrelatedSignedTerminal(context.getBean(com.acrylic.outbound.pda.OfflineSigner.class));checks.verify(profile.attempt);
            }
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Actual HTTP immutable applied receipt and pending physical clearance; synthetic registered source");
    }
}
