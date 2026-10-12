package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.PcQuarantineHttpSource;
import com.acrylic.outbound.inventory.PcQuarantineHttpChecks;
import java.nio.file.Files;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aPcQuarantineIT {
    @Test void actualPcOriginalApprovalsSurviveNonemptyUpgradeAndClearance() throws Exception {
        var profile=new E2aCrossSourceProfile(); var jdbc=profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        Flyway.configure().dataSource(jdbc.getDataSource()).target("88").load().migrate();
        signing(profile);
        PcQuarantineHttpSource source;
        try(var context=E2aCrossSourceHttpIT.start(profile,"--server.servlet.session.cookie.secure=false")) {
            source=new PcQuarantineHttpSource((WebApplicationContext)context,profile.attempt,jdbc);
        }
        var originals=source.originals(); var migration=Flyway.configure().dataSource(jdbc.getDataSource()).load();
        migration.migrate(); migration.validate(); migration.migrate(); assertThat(source.originals()).isEqualTo(originals);
        try(var context=E2aCrossSourceHttpIT.start(profile,"--server.servlet.session.cookie.secure=false",
            "--outbound.corrections.commands.enabled=true","--outbound.corrections.attachments.enabled=true")) {
            if(!"true".equals(System.getenv("PC_QUARANTINE_WEB_PREPARE"))) new PcQuarantineHttpChecks(source,profile.attempt).verify();
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Synthetic isolation stock; actual original PC HTTP approval/application; nonempty migration and clearance consumer");
    }
    private void signing(E2aCrossSourceProfile profile) throws Exception {
        var keyRoot=E2aCrossSourceProfile.ROOT.resolve(".local/it-keys");
        byte[] publicKey=Files.readAllBytes(keyRoot.resolve("server-public.der"));
        String digest=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(publicKey));
        assertThat(digest).isEqualTo("5f17c0234838910f45049ad288e4caeb93c3e7da944ccf776d69cecf2554f559");
        Files.copy(keyRoot.resolve("server-public.der"),profile.attempt.resolve("private/server-public.der"));
        Files.copy(keyRoot.resolve("server-private.der"),profile.attempt.resolve("private/server-private.der"));
    }
}
