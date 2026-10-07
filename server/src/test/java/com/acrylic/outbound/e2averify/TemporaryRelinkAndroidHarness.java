package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.TemporaryRelinkAndroidChecks;
import com.acrylic.outbound.inventory.TemporaryRelinkChecks;
import java.nio.file.Files;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Standalone server consumer; only Android runs Gradle while this process owns the isolated database. */
public final class TemporaryRelinkAndroidHarness {
    private TemporaryRelinkAndroidHarness() { }
    public static void main(String[] args) throws Exception {
        assertThat(System.getenv("E2A_CROSS_VERIFY")).isEqualTo("true");
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));
        profile.migrate(jdbc,"latest");E2aCrossSourceHttpSetup.signing(profile.attempt);
        try(var context=E2aCrossSourceHttpIT.start(profile,"--outbound.temporary.commands.enabled=true")) {
            new TemporaryRelinkChecks((WebApplicationContext)context).verify(profile.attempt);
            new TemporaryRelinkAndroidChecks((WebApplicationContext)context,profile.attempt).verify();
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Synthetic identities; real LINK/relink HTTP and Android Room recovery; candidate conflicts remain unresolved");
    }
}
