package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.OutboundApplication;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.SpringApplication;
import static org.assertj.core.api.Assertions.*;

/** Cold product entries independently authenticate the target and refuse interrupted successor state. */
final class E2aSuccessorPendingStarts {
    static void verify(E2aCrossSourceProfile profile, String[] authority, E2aArchiveRestoreTarget target) throws Exception {
        Path home = Files.createDirectory(profile.attempt.resolve("private/successor-pending"));
        E2aSplitCopyTarget.prepare(home, target);
        for (String role : List.of("api", "worker")) {
            var options = new ArrayList<>(List.of(authority));
            options.addAll(List.of("--server.address=127.0.0.1", "--server.port=" + E2aTestPorts.http(),
                "--spring.flyway.enabled=false", "--outbound.bootstrap.enabled=false", "--spring.session.jdbc.cleanup-cron=-",
                "--spring.datasource.url=" + target.url, "--spring.datasource.username=" + target.user,
                "--spring.datasource.password=" + target.password, "--outbound.storage=" + profile.attempt.resolve("restored/storage"),
                "--outbound.signing.private-key=" + profile.attempt.resolve("restored/config/server-private.der"),
                "--outbound.signing.public-key=" + profile.attempt.resolve("restored/config/server-public.der"),
                "--outbound.worker.enabled=" + role.equals("worker")));
            if (role.equals("worker")) options.addAll(List.of("--spring.main.web-application-type=none", "--spring.profiles.active=worker"));
            E2aSplitCopyTarget.configure(options, target);
            Files.writeString(home.resolve(role + "-options.json"), new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(options), StandardOpenOption.CREATE_NEW);
            refuse(home, role);
        }
    }

    private static void refuse(Path home, String role) throws Exception {
        var cp = SubmissionJson.parse(Files.readString(Path.of(System.getenv("E2A_SPLIT_CLASSPATH"))));
        var paths = new ArrayList<String>(); cp.forEach(value -> paths.add(value.asText()));
        var command = List.of(Path.of(System.getProperty("java.home"), "bin/java.exe").toString(), "-Xmx256m",
            "-Dfile.encoding=UTF-8", "-Duser.language=en", "-Duser.country=US", "-Duser.timezone=UTC",
            "-Djava.io.tmpdir=" + System.getProperty("java.io.tmpdir"), "-cp", String.join(java.io.File.pathSeparator, paths),
            Entry.class.getName(), home.toString(), role);
        Files.writeString(home.resolve(role + "-command.json"), new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(command), StandardOpenOption.CREATE_NEW);
        var process = new ProcessBuilder(command).redirectOutput(home.resolve(role + ".stdout").toFile())
            .redirectError(home.resolve(role + ".stderr").toFile()).start();
        try {
            assertThat(process.waitFor(45, TimeUnit.SECONDS)).as("pending child exits").isTrue();
            assertThat(process.exitValue()).isEqualTo(1);
            assertThat(home.resolve(role + "-unexpected-ready.marker")).doesNotExist();
            String output = Files.readString(home.resolve(role + ".stdout")) + Files.readString(home.resolve(role + ".stderr"));
            assertThat(output).contains(expectedCode());
            var result = SubmissionJson.object().put("pid", process.pid()).put("exitCode", process.exitValue()).put("role", role)
                .put("code", expectedCode());
            Files.writeString(home.resolve(role + "-refused.json"), result.toPrettyString(), StandardOpenOption.CREATE_NEW);
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue(); }
        }
    }

    private static String expectedCode() {
        return switch (System.getenv("E2A_SUCCESSOR_DEATH_POINT")) {
            case "intent_appended" -> "CONTROL_INTENT_INCOMPLETE";
            case "intent_head_staged" -> "CONTROL_UNKNOWN_ARTIFACT";
            case "intent_head_committed" -> "CONTROL_CLOSING_INTENT";
            case "history_appended", "pointer_staged" -> "CONTROL_COMMAND_INCOMPLETE";
            case "pointer_committed" -> "RECOVERY_DATABASE_ADMISSION_REFUSED";
            default -> throw new AssertionError("Unknown successor death point");
        };
    }

    public static final class Entry {
        public static void main(String[] args) throws Exception {
            Path home = Path.of(args[0]).toRealPath(); String role = args[1];
            assertThat(role).isIn("api", "worker");
            String run = System.getenv("E2A_CROSS_RUN"); assertThat(run).matches("[1-9][0-9]{0,3}");
            assertThat(home).isEqualTo(E2aWorktree.ROOT.resolve(".local/e2a-cross-20260925-" + run + "/private/successor-pending"));
            var options = new ArrayList<String>();
            SubmissionJson.parse(Files.readString(home.resolve(role + "-options.json"))).forEach(p -> options.add(p.asText()));
            E2aSplitCopyTarget.require(home, options, role);
            try (var unexpected = new SpringApplication(OutboundApplication.class).run(options.toArray(String[]::new))) {
                Files.createFile(home.resolve(role + "-unexpected-ready.marker"));
                throw new AssertionError("Interrupted successor product entry started");
            }
        }
    }
}
