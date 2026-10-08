package com.acrylic.outbound.e2averify;

import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

final class TemporaryHandoverAndroidProcess {
    static void run(Path attempt, String phase) throws Exception {
        var output = attempt.resolve("temporary-handover-android");
        var builder = new ProcessBuilder("cmd.exe", "/c", "gradlew.bat", "-p", "android", ":app:testDebugUnitTest",
            "--tests", "*TemporaryHandoverHttpTest", "--no-daemon", "--offline", "--rerun", "--max-workers=1", "--console=plain");
        builder.directory(E2aCrossSourceProfile.ROOT.toFile());
        builder.environment().put("TEMP_HANDOVER_ANDROID_HTTP_PRIVATE", attempt.resolve("private/temporary-handover-input.json").toString());
        builder.environment().put("TEMP_HANDOVER_ANDROID_PHASE", phase);
        builder.environment().remove("E2A_CROSS_PASSWORD");
        builder.redirectErrorStream(true).redirectOutput(output.resolve(phase + ".log").toFile());
        var started = FileTime.from(Instant.now()); var process = builder.start();
        Files.writeString(output.resolve(phase + "-pid.txt"), Long.toString(process.pid()));
        assertThat(process.waitFor(10, TimeUnit.MINUTES)).as("Inspect saved Android PID before retry").isTrue();
        var xml = E2aCrossSourceProfile.ROOT.resolve("android/app/build/test-results/testDebugUnitTest/TEST-com.acrylic.outbound.pda.temporary.TemporaryHandoverHttpTest.xml");
        if (Files.exists(xml) && Files.getLastModifiedTime(xml).compareTo(started) >= 0) Files.copy(xml, output.resolve(phase + ".xml"));
        assertThat(process.exitValue()).as("See retained TEMP handover Android log/XML").isZero();
        assertThat(Files.isRegularFile(output.resolve(phase + ".xml"))).isTrue();
        assertThat(Files.isRegularFile(output.resolve(phase + "-result.json"))).isTrue();
    }
}
