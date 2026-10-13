package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.exports.ExportModels;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static com.acrylic.outbound.inventory.CorrectionExportHttpClient.data;

/** Failure injection is restricted to the isolated schema; transport is real HTTP. */
final class ExportNetFailureChecks {
    private final CorrectionAccountingHttpFixture f;
    private final ExportNetJobConsumer consumer;
    ExportNetFailureChecks(CorrectionAccountingHttpFixture f, ExportNetJobConsumer consumer) {
        this.f = f; this.consumer = consumer;
    }
    void verify() throws Exception {
        invalid(); concurrent(); rollback(); permissions();
    }
    private void invalid() throws Exception {
        var command = ExportNetHttpChecks.rangeCommand("XLSX", "CURRENT_EFFECTIVE");
        data(consumer.wire.post("/exports", command.deepCopy().put("resultVersion", 1), UUID.randomUUID()), 400);
        data(consumer.wire.post("/exports", command.deepCopy().put("format", "SCAN_XLSX"), UUID.randomUUID()), 400);
        UUID key = UUID.randomUUID(); UUID id = consumer.create("/exports", command, key);
        var conflict = consumer.wire.post("/exports", command.deepCopy().put("basis", "ORIGINAL_CONFIRMED"), key);
        data(conflict, 409); Files.write(f.evidence.resolve("idempotency-conflict.json"), conflict.body());
        consumer.download(id, "replay-guard.xlsx", "latest-net-");
    }
    private void concurrent() throws Exception {
        UUID key = UUID.randomUUID(); var body = ExportNetHttpChecks.rangeCommand("XLSX", "CURRENT_EFFECTIVE");
        var start = new CountDownLatch(1); UUID id;
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<UUID> action = () -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return UUID.fromString(data(consumer.wire.post("/exports", body, key), 200).path("id").asText());
            };
            var first = pool.submit(action); var second = pool.submit(action); start.countDown();
            id = first.get(30, TimeUnit.SECONDS); assertThat(second.get(30, TimeUnit.SECONDS)).isEqualTo(id);
        }
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM background_job WHERE dedupe_key=?", Integer.class,
            "r08:" + f.makerIdentity.user().id() + ":" + key)).isEqualTo(1);
        consumer.download(id, "concurrent-single-task.xlsx", "latest-net-");
    }
    private void rollback() throws Exception {
        UUID key = UUID.randomUUID(); var before = f.original();
        int events = f.jdbc.queryForObject("SELECT COUNT(*) FROM operation_event WHERE event_type='REPORT_EXPORT_REQUESTED'", Integer.class);
        f.jdbc.execute("CREATE TRIGGER e05_fail_export_audit BEFORE INSERT ON operation_event FOR EACH ROW "
            + "BEGIN IF NEW.event_type='REPORT_EXPORT_REQUESTED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='e05-tail-injection'; END IF; END");
        try {
            var response = consumer.wire.post("/exports", ExportNetHttpChecks.rangeCommand("XLSX", "CURRENT_EFFECTIVE"), key);
            data(response, 500); Files.write(f.evidence.resolve("tail-sql-failure.json"), response.body());
        } finally { f.jdbc.execute("DROP TRIGGER e05_fail_export_audit"); }
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM background_job WHERE dedupe_key=?", Integer.class,
            "r08:" + f.makerIdentity.user().id() + ":" + key)).isZero();
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM operation_event WHERE event_type='REPORT_EXPORT_REQUESTED'", Integer.class)).isEqualTo(events);
        assertThat(f.original()).isEqualTo(before);
        UUID recovered = consumer.create("/exports", ExportNetHttpChecks.rangeCommand("XLSX", "CURRENT_EFFECTIVE"), key);
        consumer.download(recovered, "tail-recovered.xlsx", "latest-net-");
    }
    private void permissions() throws Exception {
        var other = IdentityFixture.create(f.jdbc, f.context.getBean(org.springframework.security.crypto.password.PasswordEncoder.class), "WAREHOUSE_ADMIN");
        var cross = CorrectionExportHttpClient.login(f.context, new ExportModels.Scope(other.user().id(), UUID.fromString(f.warehouse), null));
        data(cross.get(ExportNetHttpChecks.summaryPath()), 404);
        f.jdbc.update("INSERT INTO user_warehouse VALUES(?,UUID_TO_BIN(?))", Ids.bytes(other.user().id()), f.warehouse);
        String job = SubmissionJson.parse(Files.readString(f.evidence.resolve("restart-input.json"))).path("fixedJob").asText();
        data(cross.get("/exports/" + job), 404); data(cross.get("/exports/" + job + "/file"), 404);
        f.jdbc.update("DELETE FROM user_role WHERE user_id=?", Ids.bytes(other.user().id()));
        data(cross.get(ExportNetHttpChecks.summaryPath()), 403);
        f.jdbc.update("UPDATE app_user SET auth_version=auth_version+1 WHERE id=?", Ids.bytes(other.user().id()));
        data(cross.get(ExportNetHttpChecks.summaryPath()), 401);
        Files.writeString(f.evidence.resolve("failure-checks-completed.txt"), "HTTP concurrent/replay/conflict/tail rollback/cross warehouse/owner/role/session checks passed");
    }
}
