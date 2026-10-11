package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.recovery.RecoveryReceivedConfirmationRestart;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Consumes the retained scoped confirmation in a distinct process without issuing identities. */
public final class E2aConfirmationReplayMain {
    static void launch(Path attempt) throws Exception {
        var paths=new ObjectMapper().readTree(Files.readAllBytes(Path.of("build/recovery-control-classpath.json")));
        var classpath=new ArrayList<String>(); paths.forEach(path->classpath.add(path.asText()));
        var command=List.of(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),"-Dfile.encoding=UTF-8",
            "-cp",String.join(java.io.File.pathSeparator,classpath),E2aConfirmationReplayMain.class.getName());
        Files.writeString(attempt.resolve("confirmation-restart-command.json"),new ObjectMapper().writeValueAsString(command));
        var process=new ProcessBuilder(command).redirectOutput(attempt.resolve("confirmation-restart-stdout.txt").toFile())
            .redirectError(attempt.resolve("confirmation-restart-stderr.txt").toFile()).start();
        try {
            assertThat(process.waitFor(60,TimeUnit.SECONDS)).as("Confirmation child exit").isTrue();
            Files.writeString(attempt.resolve("confirmation-restart-exit.json"),new ObjectMapper().writeValueAsString(
                Map.of("pid",process.pid(),"exit",process.exitValue(),"exited",!process.isAlive())));
            assertThat(process.exitValue()).isZero();
        } finally { if(process.isAlive()){process.destroyForcibly();process.waitFor(20,TimeUnit.SECONDS);} }
    }
    public static void main(String[] args) throws Exception {
        var profile=E2aCrossSourceProfile.completedSession();
        try(var app=E2aCrossSourceHttpIT.start(profile,"--outbound.handover.commands.enabled=true",
                "--outbound.handover.fault.enabled=true","--server.servlet.session.cookie.secure=false")) {
            RecoveryReceivedConfirmationRestart.verify((WebApplicationContext)app,profile.attempt,Path.of(System.getenv("E2A_RECEIVED_SOURCE")));
        }
    }
    private E2aConfirmationReplayMain() { }
}
