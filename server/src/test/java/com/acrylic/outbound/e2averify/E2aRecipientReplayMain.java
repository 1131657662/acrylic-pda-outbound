package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.recovery.RecoveryReceivedRecipientRestart;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Test-only child JVM replays the committed enrollment with its original independently pinned evidence. */
public final class E2aRecipientReplayMain {
    static void launch(Path attempt) throws Exception {
        var paths=new ObjectMapper().readTree(Files.readAllBytes(Path.of("build/recovery-control-classpath.json")));
        var classpath=new ArrayList<String>(); paths.forEach(path->classpath.add(path.asText()));
        var command=List.of(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),"-Dfile.encoding=UTF-8",
            "-cp",String.join(java.io.File.pathSeparator,classpath),E2aRecipientReplayMain.class.getName());
        Files.writeString(attempt.resolve("recipient-restart-command.json"),new ObjectMapper().writeValueAsString(command));
        var process=new ProcessBuilder(command).redirectOutput(attempt.resolve("recipient-restart-stdout.txt").toFile())
            .redirectError(attempt.resolve("recipient-restart-stderr.txt").toFile()).start();
        try {
            assertThat(process.waitFor(60,TimeUnit.SECONDS)).as("Recipient child exit").isTrue();
            Files.writeString(attempt.resolve("recipient-restart-exit.json"),new ObjectMapper().writeValueAsString(
                Map.of("pid",process.pid(),"exit",process.exitValue(),"exited",!process.isAlive())));
            assertThat(process.exitValue()).isZero();
        } finally { if(process.isAlive()){process.destroyForcibly();process.waitFor(20,TimeUnit.SECONDS);} }
    }
    public static void main(String[] args) throws Exception {
        var profile=E2aCrossSourceProfile.completedRecipient();
        try(var app=E2aCrossSourceHttpIT.start(profile,"--outbound.handover.commands.enabled=true",
                "--outbound.handover.fault.enabled=true","--server.servlet.session.cookie.secure=false")) {
            RecoveryReceivedRecipientRestart.verify((WebApplicationContext)app,profile.attempt,Path.of(System.getenv("E2A_RECEIVED_SOURCE")));
        }
    }
    private E2aRecipientReplayMain() { }
}
