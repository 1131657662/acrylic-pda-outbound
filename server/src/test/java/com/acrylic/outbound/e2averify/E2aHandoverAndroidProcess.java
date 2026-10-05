package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

/** Runs the real Android consumer while the one-shot server remains listening. */
final class E2aHandoverAndroidProcess {
    private E2aHandoverAndroidProcess() { }

    static void run(Path attempt) throws Exception {
        run(attempt, "HandoverRealHttpTest");
    }

    static void run(Path attempt, String testClass) throws Exception {
        Path root = E2aCrossSourceProfile.ROOT;
        var builder = new ProcessBuilder("cmd.exe", "/c", "gradlew.bat", "-p", "android", ":app:testDebugUnitTest",
            "--tests", "*" + testClass, "--no-daemon", "--offline", "--rerun-tasks", "--max-workers=1", "--console=plain");
        builder.directory(root.toFile());
        builder.environment().put("HANDOVER_ANDROID_HTTP_PRIVATE", attempt.resolve("private/handover-input.json").toString());
        builder.environment().remove("E2A_CROSS_PASSWORD");
        builder.redirectErrorStream(true).redirectOutput(attempt.resolve("private/handover-android.log").toFile());
        var started = FileTime.from(Instant.now());
        var process = builder.start();
        Files.writeString(attempt.resolve("android-process-pid.txt"), Long.toString(process.pid()));
        assertThat(process.waitFor(10, TimeUnit.MINUTES)).as("Inspect saved Android PID if still live").isTrue();
        var xml = root.resolve("android/app/build/test-results/testDebugUnitTest/TEST-com.acrylic.outbound.pda.continuation." + testClass + ".xml");
        if (Files.exists(xml) && Files.getLastModifiedTime(xml).compareTo(started) >= 0)
            Files.copy(xml, attempt.resolve("android-result.xml"));
        assertThat(process.exitValue()).as("See retained private/handover-android.log and Android XML").isZero();
        assertThat(Files.isRegularFile(attempt.resolve("android-result.xml"))).isTrue();
        assertThat(Files.isRegularFile(attempt.resolve("android-result.json"))).isTrue();
    }
}
