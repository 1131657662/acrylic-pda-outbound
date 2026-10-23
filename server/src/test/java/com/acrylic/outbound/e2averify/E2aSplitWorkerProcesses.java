package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.recovery.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Owns only the two disposable child JVMs and their private input files. */
final class E2aSplitWorkerProcesses implements AutoCloseable {
    private record Target(String scope, E2aArchiveRestoreTarget destination) { }
    final Path home;
    private final Map<String, Process> children = new LinkedHashMap<>();

    E2aSplitWorkerProcesses(E2aCrossSourceProfile profile, String[] authority) throws Exception {
        this(profile, authority, "split");
    }

    E2aSplitWorkerProcesses(E2aCrossSourceProfile profile, String[] authority, String scope) throws Exception {
        this(profile, authority, new Target(scope, null));
    }

    E2aSplitWorkerProcesses(E2aCrossSourceProfile profile, String[] authority, E2aArchiveRestoreTarget destination) throws Exception {
        this(profile, authority, new Target("restored-closed-p5", destination));
    }

    static E2aSplitWorkerProcesses boundCrash(E2aCrossSourceProfile profile, String[] authority,
            E2aArchiveRestoreTarget destination) throws Exception {
        return new E2aSplitWorkerProcesses(profile, authority, new Target("restored-bound-crash", destination));
    }

    static E2aSplitWorkerProcesses boundRetirement(E2aCrossSourceProfile profile, String[] authority,
            E2aArchiveRestoreTarget destination) throws Exception {
        return new E2aSplitWorkerProcesses(profile, authority, new Target("restored-bound-retirement", destination));
    }

    private E2aSplitWorkerProcesses(E2aCrossSourceProfile profile, String[] authority, Target target) throws Exception {
        String scope = target.scope();
        assertThat(scope).isIn("split", "restored-split", "restored-split-successor", "restored-stale", "restored-p5-stale", "restored-p5-fault", "restored-p5-crash", "restored-closed-p5", "restored-bound-crash", "restored-bound-retirement");
        home = Files.createDirectory(profile.attempt.resolve("private/" + scope));
        if (target.destination() != null) E2aSplitCopyTarget.prepare(home, target.destination());
        for (String role : List.of("api", "worker")) {
            var options = options(profile, authority);
            if (!scope.equals("split")) restoredOptions(options, profile);
            if (target.destination() != null) E2aSplitCopyTarget.configure(options, target.destination());
            if (role.equals("worker")) {
                options.remove("--outbound.worker.enabled=false");
                options.addAll(List.of("--spring.main.web-application-type=none",
                    "--spring.profiles.active=worker", "--outbound.worker.enabled=true",
                    "--outbound.worker.poll-ms=250", "--outbound.worker.reminder-poll-ms=3600000"));
            }
            assertThat(options.stream().map(option -> option.substring(0, option.indexOf('='))).distinct().count())
                .isEqualTo(options.size());
            Files.writeString(home.resolve(role + "-options.json"),
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(options), StandardOpenOption.CREATE_NEW);
        }
    }

    private static void restoredOptions(ArrayList<String> options, E2aCrossSourceProfile profile) {
        Path restored = profile.attempt.resolve("restored");
        var replacements = Map.of(
            "--spring.datasource.url=", profile.url.replace("/" + profile.database + "?", "/" + profile.database + "_restore?"),
            "--outbound.storage=", restored.resolve("storage").toString(),
            "--outbound.signing.private-key=", restored.resolve("config/server-private.der").toString(),
            "--outbound.signing.public-key=", restored.resolve("config/server-public.der").toString());
        options.replaceAll(option -> replacements.entrySet().stream()
            .filter(entry -> option.startsWith(entry.getKey())).findFirst()
            .map(entry -> entry.getKey() + entry.getValue()).orElse(option));
    }

    void start(String role) throws Exception {
        assertThat(children).doesNotContainKey(role);
        Process process = launch(E2aSplitWorkerMain.class.getName(), List.of(home.toString(), role), role);
        children.put(role, process);
        await().atMost(Duration.ofSeconds(60)).until(() -> {
            assertThat(process.isAlive()).as("child %s; see private/split logs", role).isTrue();
            return Files.exists(home.resolve(role + "-ready.json"));
        });
        assertThat(SubmissionJson.parse(Files.readString(home.resolve(role + "-ready.json"))).path("pid").asLong())
            .isEqualTo(process.pid()).isNotEqualTo(ProcessHandle.current().pid());
    }

    void stop(String role) throws Exception {
        Process process = children.get(role);
        Files.writeString(home.resolve(role + "-stop.marker"), "stop owned context", StandardOpenOption.CREATE_NEW);
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).as("%s clean exit", role).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(home.resolve(role + "-closed.marker")).isRegularFile();
    }

    void successor(Path config) throws Exception {
        Process process = launch(RecoveryRetirementChildMain.class.getName(), List.of(config.toString(), "startup"), "successor");
        children.put("successor", process);
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(Files.readString(home.resolve("successor.stdout"))).contains("STARTED");
    }

    void haltWorker() throws Exception {
        Process worker = children.get("worker");
        assertThat(worker.isAlive()).isTrue();
        Files.writeString(home.resolve("halt-worker.marker"), "halt owned disposable worker only", StandardOpenOption.CREATE_NEW);
        assertThat(worker.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(worker.exitValue()).isEqualTo(71);
        assertThat(home.resolve("worker-closed.marker")).doesNotExist();
        var result = SubmissionJson.object().put("pid", worker.pid()).put("actualExitCode", worker.exitValue())
            .put("aliveAfter", worker.isAlive()).put("normalShutdown", false).put("parentPid", ProcessHandle.current().pid());
        Files.writeString(home.resolve("worker-crash-exit.json"), result.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }

    void failRetirement() throws Exception {
        Process worker = children.get("worker");
        assertThat(worker.isAlive()).isTrue();
        Files.writeString(home.resolve("fail-retirement.marker"), "close owned root with native deny-delete handle", StandardOpenOption.CREATE_NEW);
        assertThat(worker.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(worker.exitValue()).isEqualTo(70);
        assertThat(home.resolve("worker-closed.marker")).doesNotExist();
        var result = SubmissionJson.object().put("pid", worker.pid()).put("actualExitCode", worker.exitValue())
            .put("aliveAfter", worker.isAlive()).put("normalShutdown", false).put("parentPid", ProcessHandle.current().pid());
        Files.writeString(home.resolve("worker-retirement-exit.json"), result.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }

    void refuseApi() throws Exception {
        assertThat(children).isEmpty();
        Process api = launch(E2aSplitWorkerMain.class.getName(), List.of(home.toString(), "api"), "api");
        children.put("api", api);
        assertThat(api.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(api.exitValue()).isEqualTo(1);
        assertThat(Files.readString(home.resolve("api.stdout")) + Files.readString(home.resolve("api.stderr")))
            .contains("RECOVERY_LEASE_OUTSTANDING");
        assertThat(home.resolve("api-ready.json")).doesNotExist();
        assertThat(home.resolve("api-closed.marker")).doesNotExist();
        var result = SubmissionJson.object().put("pid", api.pid()).put("actualExitCode", api.exitValue())
            .put("aliveAfter", api.isAlive()).put("startupRefused", true).put("code", "RECOVERY_LEASE_OUTSTANDING");
        Files.writeString(home.resolve("api-refused.json"), result.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }

    private Process launch(String main, List<String> args, String label) throws Exception {
        var paths = SubmissionJson.parse(Files.readString(Path.of(System.getenv("E2A_SPLIT_CLASSPATH"))));
        var classpath = new ArrayList<String>(); paths.forEach(path -> classpath.add(path.asText()));
        var command = new ArrayList<String>(List.of(Path.of(System.getProperty("java.home"), "bin/java.exe").toString(),
            "-Xmx384m", "-Dfile.encoding=UTF-8", "-Duser.timezone=UTC",
            "-Djava.io.tmpdir=" + System.getProperty("java.io.tmpdir"),
            "-cp", String.join(java.io.File.pathSeparator, classpath), main));
        command.addAll(args);
        Files.writeString(home.resolve(label + "-command.json"), new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(command),
            StandardOpenOption.CREATE_NEW);
        return new ProcessBuilder(command).redirectOutput(home.resolve(label + ".stdout").toFile())
            .redirectError(home.resolve(label + ".stderr").toFile()).start();
    }

    private static ArrayList<String> options(E2aCrossSourceProfile profile, String[] authority) {
        var options = new ArrayList<String>(List.of("--server.address=127.0.0.1", "--server.port=" + E2aTestPorts.http(),
            "--spring.flyway.enabled=false", "--spring.datasource.url=" + profile.url,
            "--spring.datasource.username=" + profile.user, "--spring.datasource.password=" + System.getenv("E2A_CROSS_PASSWORD"),
            "--outbound.storage=" + profile.attempt.resolve("storage"),
            "--outbound.signing.private-key=" + profile.attempt.resolve("private/server-private.der"),
            "--outbound.signing.public-key=" + profile.attempt.resolve("private/server-public.der"),
            "--outbound.worker.enabled=false", "--outbound.bootstrap.enabled=false",
            "--spring.session.jdbc.cleanup-cron=-", "--server.servlet.session.cookie.secure=false",
            "--outbound.exports.sample-data=true", "--outbound.exports.renderer-home=" + System.getenv("E2A_SPLIT_RENDERER"),
            "--outbound.exports.browsers=" + System.getenv("E2A_SPLIT_BROWSERS")));
        options.addAll(List.of(authority));
        return options;
    }

    @Override public void close() throws Exception {
        for (Process process : children.values()) {
            if (!process.isAlive()) continue;
            process.destroy();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
            }
        }
    }
}
