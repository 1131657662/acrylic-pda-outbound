package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.exports.ExportModels;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static com.acrylic.outbound.inventory.CorrectionExportHttpClient.data;

/** Source setup is synthetic; recalled-source exclusion and export revocation cross real HTTP. */
public final class ExportNetCombinedChecks {
    private ExportNetCombinedChecks() { }
    public static void verify(WebApplicationContext context, Path attempt) throws Exception {
        var jdbc = context.getBean(JdbcTemplate.class);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean(com.acrylic.outbound.common.RequestIdFilter.class))
            .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        var fixture = new FaultRestartFixture(mvc, context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class),
            jdbc, context.getBean(org.springframework.security.crypto.password.PasswordEncoder.class), false);
        var http = new FaultWithdrawalHttp(context, fixture);
        var c = new ExportNetJobConsumer(context, http.web, attempt.resolve("export-combined"));
        recalledSource(http, c);
        var scope = new ExportModels.Scope(fixture.source.web.identity().user().id(), fixture.source.web.identity().warehouse(),
            UUID.fromString(fixture.source.order));
        UUID job = c.create("/orders/" + scope.order() + "/exports",
            SubmissionJson.object().put("format", "XLSX").put("basis", "CURRENT_EFFECTIVE"), UUID.randomUUID());
        c.download(job, "republished-new-source.xlsx", "latest-net-");
        for (String action : List.of("create", "retry", "download")) revocation(context, scope, new Target(job, action, c.evidence));
        ExportNetCapacityChecks.verify(context, attempt);
    }

    private static void recalledSource(FaultWithdrawalHttp h, ExportNetJobConsumer c) throws Exception {
        assertThat(ExportNetHttpChecks.summary(c).path("orderCount").asInt()).isZero();
        var withdrawn = h.withdraw(h.f.withdrawal(false), UUID.randomUUID());
        data(withdrawn, 200); Files.write(c.evidence.resolve("withdrawn.json"), withdrawn.body());
        assertThat(h.submit(h.f.source.body, h.f.source.tokens).statusCode()).isEqualTo(409);
        assertThat(ExportNetHttpChecks.summary(c).path("orderCount").asInt()).isZero();
        data(h.web.post("/orders/" + h.f.source.order + "/publish", h.f.orders.release(h.f.version(), "1"), UUID.randomUUID()), 200);
        assertThat(ExportNetHttpChecks.summary(c).path("orderCount").asInt()).isZero();
        h.claimAndDownload(); var body = h.f.body();
        var submitted = h.submit(body, h.f.tokens); assertThat(data(submitted, 200).path("status").asText()).isEqualTo("SUCCEEDED");
        Files.write(c.evidence.resolve("new-submission.json"), submitted.body());
        assertThat(h.submit(h.f.source.body, h.f.source.tokens).statusCode()).isEqualTo(409);
        assertThat(ExportNetHttpChecks.summary(c).path("actualCount").asInt()).isEqualTo(2);
        UUID job = c.create("/exports", ExportNetHttpChecks.rangeCommand("XLSX", "CURRENT_EFFECTIVE"), UUID.randomUUID());
        var snapshot = c.snapshot(job); assertThat(snapshot.orders()).hasSize(1);
        assertThat(snapshot.orders().getFirst().originalLines()).allSatisfy(line ->
            assertThat(line.originDeviceId()).isEqualTo(h.f.tokens.path("deviceId").asText()));
        c.download(job, "recalled-source-excluded.xlsx", "latest-net-");
        assertThat(h.f.source.count("dispatch_record")).isEqualTo(2);
        assertThat(h.f.source.count("inventory_ledger")).isEqualTo(2);
    }

    private record Target(UUID job, String action, Path evidence) { }
    private static void revocation(WebApplicationContext context, ExportModels.Scope scope, Target target) throws Exception {
        var jdbc = context.getBean(JdbcTemplate.class); var wire = CorrectionExportHttpClient.login(context, scope);
        UUID key = UUID.randomUUID();
        try (var connection = jdbc.getDataSource().getConnection(); var pool = Executors.newSingleThreadExecutor()) {
            connection.setAutoCommit(false);
            try (var lock = connection.prepareStatement("SELECT id FROM app_user WHERE id=? FOR UPDATE")) {
                lock.setBytes(1, Ids.bytes(scope.actor())); lock.executeQuery().close();
            }
            var response = pool.submit(() -> switch (target.action()) {
                case "create" -> wire.post("/exports", ExportNetHttpChecks.rangeCommand("XLSX", "CURRENT_EFFECTIVE"), key);
                case "retry" -> wire.post("/exports/" + target.job() + "/retry", SubmissionJson.object(), key);
                default -> wire.get("/exports/" + target.job() + "/file");
            });
            awaitBlocked(jdbc);
            try (var update = connection.prepareStatement("UPDATE app_user SET auth_version=auth_version+1 WHERE id=?")) {
                update.setBytes(1, Ids.bytes(scope.actor())); assertThat(update.executeUpdate()).isEqualTo(1);
            }
            connection.commit(); var result = response.get(30, TimeUnit.SECONDS); data(result, 401);
            Files.write(target.evidence().resolve(target.action() + "-revocation-race.json"), result.body());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM background_job WHERE dedupe_key=?", Integer.class,
            "r08:" + scope.actor() + ":" + key)).isZero();
    }

    private static void awaitBlocked(JdbcTemplate jdbc) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20); int waits = 0;
        while (waits == 0 && System.nanoTime() < until) {
            waits = jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits", Integer.class);
            if (waits == 0) Thread.sleep(50);
        }
        assertThat(waits).isPositive();
    }
}
