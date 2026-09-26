package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.E2aAndroidPreparation;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aDraftEvidenceIT {
    @Test void nonemptyV37AndActualAndroidOriginalsSurviveServerRestartWithoutCreatingBusinessSource() throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));
        var before=business(jdbc);assertThat(before.get("submission")).isPositive();
        profile.migrate(jdbc,"37");E2aCrossSourceHttpSetup.signing(profile.attempt);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND EVENT_OBJECT_TABLE LIKE 'local_draft_evidence%'",Integer.class)).isEqualTo(8);
        try(var context=E2aCrossSourceHttpIT.start(profile)) {
            E2aAndroidPreparation.write((WebApplicationContext)context,profile.attempt.resolve("private"));
            android(profile.attempt);new E2aDraftEvidenceChecks(jdbc,profile.attempt).database();
        }
        assertThat(business(jdbc)).isEqualTo(before);
        Path rotation=profile.attempt.resolve("rotation");Files.createDirectories(rotation.resolve("private"));
        E2aCrossSourceHttpSetup.signing(rotation);
        try(var context=E2aCrossSourceHttpIT.start(profile,
            "--outbound.signing.private-key="+rotation.resolve("private/server-private.der"),
            "--outbound.signing.public-key="+rotation.resolve("private/server-public.der"),
            "--outbound.signing.historical-public-keys="+profile.attempt.resolve("private/server-public.der"))) {
            new E2aDraftEvidenceChecks(jdbc,profile.attempt).httpAfterRestart();
        }
        assertThat(business(jdbc)).isEqualTo(before);
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Nonempty V34 -> V37; Android actual audit archive and Room stop; rotated server key + trusted old key, restart, original replay, corrupt snapshot/archive rejection; no S/K/release\n");
    }
    private Map<String,Long> business(JdbcTemplate jdbc) {
        var result=new LinkedHashMap<String,Long>();
        for(String table:List.of("submission","submission_scope","bale_commitment","dispatch_record","temporary_source_mapping","temporary_decision_state"))
            result.put(table,jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class));
        return result;
    }
    private void android(Path attempt) throws Exception {
        var root=Path.of("D:/acrylic-pda-outbound");
        var builder=new ProcessBuilder("cmd.exe","/c","gradlew.bat","-p","android",":app:testDebugUnitTest",
            "--tests","*LocalDraftEvidenceHttpTest","--no-daemon","--max-workers=1","--console=plain");
        builder.directory(root.toFile());builder.environment().remove("E2A_CROSS_PASSWORD");
        builder.environment().put("DRAFT_EVIDENCE_HTTP_PRIVATE",attempt.resolve("private/android-http-input.json").toString());
        builder.redirectErrorStream(true).redirectOutput(attempt.resolve("private/android-draft.log").toFile());
        var process=builder.start();Files.writeString(attempt.resolve("android-process-pid.txt"),Long.toString(process.pid()));
        assertThat(process.waitFor(10,TimeUnit.MINUTES)).as("Android process must finish; inspect saved PID if still live").isTrue();
        var xml=root.resolve("android/app/build/test-results/testDebugUnitTest/TEST-com.acrylic.outbound.pda.evidence.LocalDraftEvidenceHttpTest.xml");
        if(Files.exists(xml))Files.copy(xml,attempt.resolve("android-draft-result.xml"));
        assertThat(process.exitValue()).as("See protected android-draft.log").isZero();
        assertThat(Files.exists(attempt.resolve("draft-android-result.json"))).isTrue();
    }
}
