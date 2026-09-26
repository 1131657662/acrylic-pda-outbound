package com.acrylic.outbound.e2averify;

import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

final class E2aTemporaryCandidatesBrowser {
    private E2aTemporaryCandidatesBrowser() { }
    static void verify(Path attempt) throws Exception {
        var builder=new ProcessBuilder("cmd.exe","/c","npx.cmd","playwright","test","--config=temporary-candidates.config.ts");
        builder.directory(Path.of("D:/acrylic-pda-outbound/web").toFile());builder.environment().remove("E2A_CROSS_PASSWORD");
        builder.environment().put("TEMP_CANDIDATE_PRIVATE",attempt.resolve("private/temporary-candidates-browser.json").toString());
        builder.environment().put("TEMP_CANDIDATE_OUTPUT",attempt.resolve("browser").toString());
        builder.environment().put("OUTBOUND_API_PROXY","http://127.0.0.1:19434");
        builder.redirectErrorStream(true).redirectOutput(attempt.resolve("private/browser.log").toFile());
        var process=builder.start();Files.writeString(attempt.resolve("browser-process-pid.txt"),Long.toString(process.pid()));
        assertThat(process.waitFor(5,TimeUnit.MINUTES)).as("Browser runner must finish; inspect PID before retry").isTrue();
        assertThat(process.exitValue()).as("Protected browser.log holds the result").isZero();
    }
}
