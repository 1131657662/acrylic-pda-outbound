package com.acrylic.outbound.e2averify;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

/** Separate Android JVMs consume the original unknown Room before and after SQL/files restore. */
final class E2aTemporaryRepositoryAndroid {
    static void run(Path attempt,String phase) throws Exception {
        var root=E2aCrossSourceProfile.ROOT;
        var command=List.of("cmd.exe","/c","gradlew.bat","-p","android",":app:testDebugUnitTest","--rerun",
            "--tests","*TemporaryRepositoryRestoreHttpTest","--offline","--no-daemon","--max-workers=1","--console=plain");
        var builder=new ProcessBuilder(command).directory(root.toFile());
        builder.environment().remove("E2A_CROSS_PASSWORD");
        builder.environment().put("E2A_TEMP_REPOSITORY_PRIVATE",attempt.resolve("private/temp-repository-input.json").toString());
        builder.environment().put("E2A_TEMP_REPOSITORY_PHASE",phase);
        builder.redirectErrorStream(true).redirectOutput(attempt.resolve("temp-product-"+phase+".log").toFile());
        Files.writeString(attempt.resolve("temp-product-"+phase+"-command.json"),
            new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(command),StandardOpenOption.CREATE_NEW);
        var process=builder.start();
        Files.writeString(attempt.resolve("temp-product-"+phase+"-pid.txt"),Long.toString(process.pid()),StandardOpenOption.CREATE_NEW);
        assertThat(process.waitFor(12,TimeUnit.MINUTES)).as("Preserve PID if Android times out").isTrue();
        var xml=root.resolve("android/app/build/test-results/testDebugUnitTest/TEST-com.acrylic.outbound.pda.temporary.TemporaryRepositoryRestoreHttpTest.xml");
        if(Files.exists(xml))Files.copy(xml,attempt.resolve("temp-product-"+phase+".xml"));
        assertThat(process.exitValue()).as("See TEMP product phase log").isZero();
        assertThat(Files.exists(attempt.resolve("temp-product-"+phase+"-completed.json"))).isTrue();
    }
}
