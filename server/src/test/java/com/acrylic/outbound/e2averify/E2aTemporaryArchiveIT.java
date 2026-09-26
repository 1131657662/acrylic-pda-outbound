package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aTemporaryArchiveIT {
    @Test void originalAndroidFreezeCompletesSuccessAndArchiveChainOnNonemptyMysql() throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission",Integer.class)).isPositive();
        profile.migrate(jdbc,"36");E2aCrossSourceHttpSetup.signing(profile.attempt);
        try(var context=E2aCrossSourceHttpIT.start(profile)) {
            var web=(WebApplicationContext)context;new TemporaryArchiveChecks(web).verify();
            E2aAndroidPreparation.write(web,profile.attempt.resolve("private"));
            var input=SubmissionJson.parse(Files.readString(profile.attempt.resolve("private/android-http-input.json")));
            new TemporaryArchiveDriver(web).grantTestConfirmation(input.path("accessToken").asText());
            try(var adapter=new TemporaryArchiveHttpAdapter(web,profile.attempt)) { android(profile.attempt); }
            var result=SubmissionJson.parse(Files.readString(profile.attempt.resolve("android-archive-completed.json")));
            assertThat(result.path("productState").asText()).isEqualTo("SYNCED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission_archive WHERE submission_id=UUID_TO_BIN(?)",Integer.class,result.path("submissionId").asText())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_record WHERE submission_id=UUID_TO_BIN(?)",Integer.class,result.path("submissionId").asText())).isEqualTo(2);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Actual Android freeze/upload/confirmation/success/archive; internal adapter only\n");
    }
    private void android(Path attempt) throws Exception {
        var root=Path.of("D:/acrylic-pda-outbound");
        var builder=new ProcessBuilder("cmd.exe","/c","gradlew.bat","-p","android",":app:testDebugUnitTest",
            "--tests","*TemporaryArchive*Test","--no-daemon","--max-workers=1","--console=plain");
        builder.directory(root.toFile());builder.environment().remove("E2A_CROSS_PASSWORD");
        builder.environment().put("TEMP_ARCHIVE_HTTP_PRIVATE",attempt.resolve("private/android-http-input.json").toString());
        builder.redirectErrorStream(true).redirectOutput(attempt.resolve("private/android-archive.log").toFile());
        var process=builder.start();Files.writeString(attempt.resolve("android-process-pid.txt"),Long.toString(process.pid()));
        assertThat(process.waitFor(10,TimeUnit.MINUTES)).as("Android process must finish; inspect PID if still running").isTrue();
        try(var files=Files.list(root.resolve("android/app/build/test-results/testDebugUnitTest"))) {
            for(var file:files.filter(p->p.getFileName().toString().contains("TemporaryArchive")&&p.toString().endsWith(".xml")).toList())Files.copy(file,attempt.resolve(file.getFileName()));
        }
        assertThat(process.exitValue()).as("See protected android-archive.log").isZero();
        assertThat(Files.exists(attempt.resolve("android-archive-completed.json"))).isTrue();
    }
}
