package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.OutboundApplication;
import com.acrylic.outbound.jobs.BackgroundWorkerProcess;
import com.acrylic.outbound.recovery.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.time.Duration;
import java.util.ArrayList;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Disposable API or production nonweb scheduler, never a shared or production database. */
public final class E2aSplitWorkerMain {
    public static void main(String[] args) throws Exception {
        Path home = Path.of(args[0]).toRealPath();
        boolean worker = args[1].equals("worker");
        assertThat(args[1]).isIn("api", "worker");
        var options = new ArrayList<String>();
        SubmissionJson.parse(Files.readString(home.resolve(args[1] + "-options.json")))
            .forEach(value -> options.add(value.asText()));
        boolean restored = identity(home, options, args[1]);
        Class<?>[] sources = worker ? new Class<?>[]{OutboundApplication.class, RecoveryWorkerProbe.Configuration.class}
            : new Class<?>[]{OutboundApplication.class};
        try (var context = new SpringApplication(sources).run(options.toArray(String[]::new))) {
            assertThat(context instanceof WebApplicationContext).isEqualTo(!worker);
            assertThat(context.getBeansOfType(BackgroundWorkerProcess.class).size()).isEqualTo(worker ? 1 : 0);
            var ready = SubmissionJson.object().put("pid", ProcessHandle.current().pid())
                .put("role", args[1]).put("web", !worker);
            if (home.getFileName().toString().equals("restored-closed-p5"))
                RecoveryRegisteredP5Checks.observe(context, home, args[1]);
            Files.writeString(home.resolve(args[1] + "-ready.json"), ready.toPrettyString(), StandardOpenOption.CREATE_NEW);
            if (home.getFileName().toString().equals("restored-bound-retirement"))
                RecoveryBoundRetirementChecks.serve(context, home, worker);
            else if (home.getFileName().toString().equals("restored-bound-crash"))
                RecoveryBoundCrashChecks.serve(context, home, worker);
            else if (home.getFileName().toString().equals("restored-p5-crash"))
                RecoveryRestoredCrashChecks.serve(context, home, worker);
            else if (home.getFileName().toString().equals("restored-p5-fault"))
                RecoveryRestoredP5StaleChecks.serve(context, home, worker);
            else if (home.getFileName().toString().equals("restored-p5-stale"))
                RecoveryRestoredP5StaleChecks.serve(context, home, worker);
            else if (home.getFileName().toString().equals("restored-stale"))
                RecoveryRestoredStaleChecks.serve(context, home, worker);
            else {
                if (worker && !restored) probe(context, home);
                await().atMost(Duration.ofSeconds(240)).until(() -> Files.exists(home.resolve(args[1] + "-stop.marker")));
                RecoverySplitWorkerChecks.drain(context);
            }
        }
        Files.writeString(home.resolve(args[1] + "-closed.marker"), "clean context shutdown", StandardOpenOption.CREATE_NEW);
    }

    private static void probe(org.springframework.context.ConfigurableApplicationContext context, Path home) throws Exception {
        await().atMost(Duration.ofSeconds(240)).until(() -> Files.exists(home.resolve("probe-start.marker")));
        RecoverySplitWorkerChecks.probe(context, home);
    }

    private static boolean identity(Path home, ArrayList<String> options, String role) throws Exception {
        String run = System.getenv("E2A_CROSS_RUN");
        assertThat(run).matches("[1-9][0-9]{0,3}");
        Path attempt = E2aWorktree.ROOT.resolve(".local/e2a-cross-20260925-" + run);
        String scope = home.getFileName().toString();
        assertThat(scope).isIn("split", "restored-split", "restored-split-successor", "restored-stale", "restored-p5-stale", "restored-p5-fault", "restored-p5-crash", "restored-closed-p5", "restored-bound-crash", "restored-bound-retirement");
        assertThat(home).isEqualTo(attempt.resolve("private/" + scope));
        if (Files.isRegularFile(home.resolve("cross-instance-target.json"))) {
            assertThat(scope).isIn("restored-closed-p5", "restored-bound-crash", "restored-bound-retirement");
            E2aSplitCopyTarget.require(home, options, role);
            return true;
        }
        boolean restored = !scope.equals("split");
        String database = "acrylic_e2a_cross_20260925_" + run + (restored ? "_restore" : "");
        String user = "e2a_cross_20260925_" + run;
        String url = "jdbc:mysql://127.0.0.1:" + E2aTestPorts.database() + "/" + database
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        E2aRestoredWorkerProcess.identity(new JdbcTemplate(new DriverManagerDataSource(url, user,
            System.getenv("E2A_CROSS_PASSWORD"))), attempt, database, user);
        assertThat(options).contains("--spring.datasource.url=" + url, "--spring.datasource.username=" + user);
        Path storage = restored ? attempt.resolve("restored/storage") : attempt.resolve("storage");
        assertThat(options).contains("--outbound.storage=" + storage);
        return restored;
    }
}
