package com.acrylic.outbound.e2averify;

import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

/** Distinct reports and Room copy preserve the earlier receiving stage. */
final class E2aFormalFaultSuccessProcess {
    static void run(Path attempt) throws Exception {
        var root = E2aCrossSourceProfile.ROOT;
        var builder = new ProcessBuilder("cmd.exe", "/c", "gradlew.bat", "-p", "android", ":app:testDebugUnitTest",
            "--tests", "*HandoverFormalFaultSuccessHttpTest", "--no-daemon", "--offline", "--max-workers=1", "--console=plain", "--rerun");
        builder.directory(root.toFile());
        builder.environment().put("HANDOVER_ANDROID_HTTP_PRIVATE", attempt.resolve("private/handover-input.json").toString());
        builder.environment().remove("E2A_CROSS_PASSWORD");
        builder.redirectErrorStream(true).redirectOutput(attempt.resolve("private/formal-success-android.log").toFile());
        var started = java.time.Instant.now(); var process = builder.start();
        Files.writeString(attempt.resolve("formal-success-process.pid"), Long.toString(process.pid()));
        assertThat(process.waitFor(10, TimeUnit.MINUTES)).as("Inspect retained success PID/log").isTrue();
        var xml = root.resolve("android/app/build/test-results/testDebugUnitTest/TEST-com.acrylic.outbound.pda.continuation.HandoverFormalFaultSuccessHttpTest.xml");
        if (Files.exists(xml) && Files.getLastModifiedTime(xml).toInstant().isAfter(started)) Files.copy(xml, attempt.resolve("formal-success-android.xml"));
        assertThat(process.exitValue()).as("See formal-success-android log/XML").isZero();
        assertThat(Files.isRegularFile(attempt.resolve("formal-success-android.xml"))).isTrue();
        assertThat(Files.isRegularFile(attempt.resolve("formal-success-android.json"))).isTrue();
    }
}
