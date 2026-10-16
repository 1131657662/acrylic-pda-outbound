package com.acrylic.outbound.e2averify;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

/** Sequential Android builds, exact XML and durable native SQLite evidence. */
final class E2aAndroidArchiveClient {
    static void run(Path attempt,String phase) throws Exception {
        var root=E2aCrossSourceProfile.ROOT;
        var command=List.of("cmd.exe","/c","gradlew.bat","-p","android",":app:testDebugUnitTest","--rerun",
            "--tests","*TemporaryArchiveRestoreHttpTest","--offline","--no-daemon","--max-workers=1",
            "--console=plain");
        var builder=new ProcessBuilder(command).directory(root.toFile());
        builder.environment().remove("E2A_CROSS_PASSWORD");
        builder.environment().put("E2A_ANDROID_HTTP_PRIVATE",attempt.resolve("private/android-http-input.json").toString());
        builder.environment().put("E2A_ARCHIVE_RESTORE_PHASE",phase);
        builder.redirectErrorStream(true).redirectOutput(attempt.resolve("android-"+phase+".log").toFile());
        Files.writeString(attempt.resolve("android-"+phase+"-command.json"),
            new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(command),StandardOpenOption.CREATE_NEW);
        var process=builder.start();
        Files.writeString(attempt.resolve("android-"+phase+"-pid.txt"),Long.toString(process.pid()),StandardOpenOption.CREATE_NEW);
        assertThat(process.waitFor(12,TimeUnit.MINUTES)).as("Android phase finished; preserve PID on timeout").isTrue();
        var xml=root.resolve("android/app/build/test-results/testDebugUnitTest/TEST-com.acrylic.outbound.pda.temporary.TemporaryArchiveRestoreHttpTest.xml");
        if(Files.exists(xml))Files.copy(xml,attempt.resolve("android-"+phase+".xml"));
        assertThat(process.exitValue()).as("See Android phase log").isZero();
        assertThat(Files.exists(attempt.resolve("android-"+phase+"-completed.json"))).isTrue();
    }
}
