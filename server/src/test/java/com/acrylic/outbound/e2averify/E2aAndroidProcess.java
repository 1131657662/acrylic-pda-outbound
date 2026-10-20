package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

final class E2aAndroidProcess {
    private static final Path ANDROID_TREE = Path.of(System.getenv().getOrDefault("E2A_ANDROID_TREE", E2aWorktree.ROOT.toString()));
    private E2aAndroidProcess() { }

    static void run(Path attempt) throws Exception {
        var log = attempt.resolve("private/android-http.log");
        var result = attempt.resolve("android-result.xml");
        assertThat(Files.exists(log) || Files.exists(result)).as("Never replace prior Android evidence").isFalse();
        var command = new ArrayList<>(List.of("cmd.exe", "/c", "gradlew.bat", "-p", "android"));
        configuredArgument(command, "E2A_ANDROID_INIT", "-I");
        configuredArgument(command, "E2A_ANDROID_PROJECT_CACHE", "--project-cache-dir");
        command.addAll(List.of(":app:testDebugUnitTest", "--tests", "*E2aRealHttpRecoveryTest", "--offline",
            "--no-daemon", "--max-workers=1", "--console=plain", "-Pkotlin.compiler.execution.strategy=in-process"));
        var builder = new ProcessBuilder(command);
        builder.directory(ANDROID_TREE.toFile());
        builder.environment().put("E2A_ANDROID_HTTP_PRIVATE", attempt.resolve("private/android-http-input.json").toString());
        builder.environment().remove("E2A_CROSS_PASSWORD");
        builder.environment().remove("OUTBOUND_DB_PASSWORD");
        builder.redirectErrorStream(true).redirectOutput(log.toFile());
        var process = builder.start();
        Files.writeString(attempt.resolve("android-process-pid.txt"), Long.toString(process.pid()));
        assertThat(process.waitFor(10, TimeUnit.MINUTES)).as("Android process must finish; inspect saved PID if live").isTrue();
        var xml = Path.of(System.getenv().getOrDefault("E2A_ANDROID_RESULT_XML", ANDROID_TREE.resolve(
            "android/app/build/test-results/testDebugUnitTest/TEST-com.acrylic.outbound.pda.evidence.E2aRealHttpRecoveryTest.xml").toString()));
        if (Files.exists(xml)) Files.copy(xml, result);
        assertThat(process.exitValue()).as("See protected android-http.log and retained XML").isZero();
        assertThat(Files.isRegularFile(attempt.resolve("android-result.json"))).isTrue();
    }

    private static void configuredArgument(List<String> command, String variable, String flag) {
        String value = System.getenv(variable);
        if (value != null && !value.isBlank()) { command.add(flag); command.add(value); }
    }
}
