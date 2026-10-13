package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.QuarantineHistoryHttpSource;
import com.acrylic.outbound.inventory.QuarantineHistoryHttpChecks;
import java.nio.file.Files;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aQuarantineHistoryContributionIT {
    @Test void originalReversalAndStockRestorationProveANewCyclesQuarantineClearance() throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));
        Flyway.configure().dataSource(jdbc.getDataSource()).load().migrate();signing(profile);
        try(var app=E2aCrossSourceHttpIT.start(profile,"--server.servlet.session.cookie.secure=false",
            "--outbound.corrections.commands.enabled=true","--outbound.corrections.attachments.enabled=true")) {
            var source=new QuarantineHistoryHttpSource((WebApplicationContext)app,profile.attempt);
            if(!"true".equals(System.getenv("QUARANTINE_HISTORY_WEB_PREPARE")))new QuarantineHistoryHttpChecks(source).verify();
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Original TEMP confirmation, actual physical return/stock restoration and new PC quarantine; "
            +("true".equals(System.getenv("QUARANTINE_HISTORY_WEB_PREPARE"))?"browser preparation only":"actual independent clearance without changing old facts"));
    }
    private void signing(E2aCrossSourceProfile profile) throws Exception {
        var root=E2aCrossSourceProfile.ROOT.resolve(".local/it-keys");
        byte[] key=Files.readAllBytes(root.resolve("server-public.der"));
        assertThat(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(key)))
            .isEqualTo("5f17c0234838910f45049ad288e4caeb93c3e7da944ccf776d69cecf2554f559");
        Files.copy(root.resolve("server-public.der"),profile.attempt.resolve("private/server-public.der"));
        Files.copy(root.resolve("server-private.der"),profile.attempt.resolve("private/server-private.der"));
    }
}
