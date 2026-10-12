package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.TemporaryExclusionRestartChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aTemporaryExclusionRestartIT {
    @Test void readsOriginalSelectionEvidenceInNewJvm() throws Exception {
        var profile=E2aCrossSourceProfile.completedExclusion();
        try(var context=E2aCrossSourceHttpIT.start(profile,"--outbound.temporary.commands.enabled=true")) {
            TemporaryExclusionRestartChecks.verify(profile.attempt,context.getBean(JdbcTemplate.class));
        }
        Files.writeString(profile.attempt.resolve("exclusion-restart-completed.marker"),"New JVM HTTP exclusion/link/confirm originals unchanged\n");
    }
}
