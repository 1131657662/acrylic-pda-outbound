package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

final class E2aAndroidProcess {
    private static final Path ANDROID_TREE = Path.of("D:/acrylic-pda-outbound");
    private E2aAndroidProcess() { }

    static void run(Path attempt) throws Exception {
        var builder = new ProcessBuilder("cmd.exe", "/c", "gradlew.bat", "-p", "android", ":app:testDebugUnitTest",
            "--tests", "*E2aRealHttpRecoveryTest", "--no-daemon", "--max-workers=1", "--console=plain");
        builder.directory(ANDROID_TREE.toFile());
        builder.environment().put("E2A_ANDROID_HTTP_PRIVATE", attempt.resolve("private/android-http-input.json").toString());
        builder.environment().remove("E2A_CROSS_PASSWORD");
        builder.redirectErrorStream(true).redirectOutput(attempt.resolve("private/android-http.log").toFile());
        var process = builder.start();
        Files.writeString(attempt.resolve("android-process-pid.txt"), Long.toString(process.pid()));
        assertThat(process.waitFor(10, TimeUnit.MINUTES)).as("Android process must finish; inspect saved PID if live").isTrue();
        var xml = ANDROID_TREE.resolve("android/app/build/test-results/testDebugUnitTest/TEST-com.acrylic.outbound.pda.evidence.E2aRealHttpRecoveryTest.xml");
        if (Files.exists(xml)) Files.copy(xml, attempt.resolve("android-result.xml"));
        assertThat(process.exitValue()).as("See protected android-http.log and retained XML").isZero();
        assertThat(Files.isRegularFile(attempt.resolve("android-result.json"))).isTrue();
    }
}
