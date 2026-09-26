package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.TemporarySuccessConsumerVectors;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aTemporarySuccessConsumerIT {
    @Test void androidRetainsActualTemporarySuccessOnNonemptyMysqlAndHttp() throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission",Integer.class)).isPositive();
        profile.migrate(jdbc,"36");E2aCrossSourceHttpSetup.signing(profile.attempt);
        try(var context=E2aCrossSourceHttpIT.start(profile)) {
            new TemporarySuccessConsumerVectors((WebApplicationContext)context).write(profile.attempt);
            android(profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"TEMP success consumer; real HTTP; product confirmation remains closed\n");
    }

    private void android(Path attempt) throws Exception {
        var root=Path.of("D:/acrylic-pda-outbound");
        var builder=new ProcessBuilder("cmd.exe","/c","gradlew.bat","-p","android",":app:testDebugUnitTest",
            "--tests","*TemporarySuccess*Test","--no-daemon","--max-workers=1","--console=plain");
        builder.directory(root.toFile());builder.environment().remove("E2A_CROSS_PASSWORD");
        builder.environment().put("TEMP_SUCCESS_HTTP_PRIVATE",attempt.resolve("private/android-success-input.json").toString());
        builder.redirectErrorStream(true).redirectOutput(attempt.resolve("private/android-success.log").toFile());
        var process=builder.start();Files.writeString(attempt.resolve("android-process-pid.txt"),Long.toString(process.pid()));
        assertThat(process.waitFor(10,TimeUnit.MINUTES)).as("Android process finished; inspect saved PID if live").isTrue();
        try(var files=Files.list(root.resolve("android/app/build/test-results/testDebugUnitTest"))) {
            for(var file:files.filter(p->p.getFileName().toString().contains("TemporarySuccess")&&p.toString().endsWith(".xml")).toList())
                Files.copy(file,attempt.resolve(file.getFileName()));
        }
        assertThat(process.exitValue()).as("See protected android-success.log").isZero();
        assertThat(Files.exists(attempt.resolve("android-success-completed.json"))).isTrue();
    }
}
