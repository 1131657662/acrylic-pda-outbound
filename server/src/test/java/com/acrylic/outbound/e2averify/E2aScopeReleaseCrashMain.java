package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.recovery.RecoveryReceivedScopeReleaseRestart;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Test-only real child process halts at the confirmed DB / absent external COMMIT boundary. */
public final class E2aScopeReleaseCrashMain {
    static void launch(Path attempt) throws Exception {
        var paths=new ObjectMapper().readTree(Files.readAllBytes(Path.of("build/recovery-control-classpath.json")));
        var classpath=new ArrayList<String>(); paths.forEach(path->classpath.add(path.asText()));
        var command=List.of(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),"-Dfile.encoding=UTF-8",
            "-cp",String.join(java.io.File.pathSeparator,classpath),E2aScopeReleaseCrashMain.class.getName());
        Files.writeString(attempt.resolve("scope-crash-command.json"),new ObjectMapper().writeValueAsString(command));
        var process=new ProcessBuilder(command).redirectOutput(attempt.resolve("scope-crash-stdout.txt").toFile())
            .redirectError(attempt.resolve("scope-crash-stderr.txt").toFile()).start();
        try {
            assertThat(process.waitFor(90,TimeUnit.SECONDS)).as("Crash child exit").isTrue();
            Files.writeString(attempt.resolve("scope-crash-exit.json"),new ObjectMapper().writeValueAsString(
                Map.of("pid",process.pid(),"exit",process.exitValue(),"exited",!process.isAlive())));
            assertThat(process.exitValue()).isEqualTo(73);
        } finally { if(process.isAlive()){process.destroyForcibly();process.waitFor(20,TimeUnit.SECONDS);} }
    }
    public static void main(String[] args) throws Exception {
        var profile=E2aCrossSourceProfile.completedScopeRelease();
        assertThat(Files.isRegularFile(profile.attempt.resolve("scope-restart-started.marker"))).isTrue();
        try(var app=E2aCrossSourceHttpIT.start(profile,"--outbound.handover.commands.enabled=true",
                "--outbound.handover.fault.enabled=true","--server.servlet.session.cookie.secure=false")) {
            RecoveryReceivedScopeReleaseRestart.crashAfterReadback((WebApplicationContext)app,profile.attempt,Path.of(System.getenv("E2A_RECEIVED_SOURCE")));
        }
        throw new AssertionError("Crash boundary did not halt");
    }
    private E2aScopeReleaseCrashMain() { }
}
