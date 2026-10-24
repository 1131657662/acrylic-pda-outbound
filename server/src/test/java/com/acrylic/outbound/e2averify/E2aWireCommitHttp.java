package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.RecoveryExportInput;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** A third real export observes completion visibility with a separate JDBC connection. */
final class E2aWireCommitHttp {
    private final E2aCrossSourceProfile profile;
    private final RecoveryExportInput input;
    private final RecoveryWorkerHttp http;
    private final String original;
    private String job;

    E2aWireCommitHttp(E2aCrossSourceProfile profile, RecoveryExportInput input, String original) throws Exception {
        this.profile = profile; this.input = input; this.original = original;
        http = new RecoveryWorkerHttp(profile.attempt.resolve("private/http-commit")); http.login(input.username());
    }

    void create() throws Exception {
        String key = UUID.randomUUID().toString();
        job = http.create(input.exportPath(), "XLSX", key, 200).at("/data/id").asText();
        save("commit-request.json", SubmissionJson.object().put("id", job).put("key", key));
    }

    ObjectNode observe(JdbcTemplate target, E2aArchiveSnapshotRestore copier, boolean committed) throws Exception {
        var row = target.queryForMap("SELECT status,attempt_count AS attempts,CAST(payload AS CHAR) AS payload "
            + "FROM background_job WHERE id=UUID_TO_BIN(?)", job);
        assertThat(row.get("status")).isEqualTo(committed ? "SUCCEEDED" : "RUNNING");
        assertThat(((Number) row.get("attempts")).intValue()).isEqualTo(1);
        var payload = SubmissionJson.parse(row.get("payload").toString());
        assertThat(!payload.path("artifact").isNull()).isEqualTo(committed);
        int audits = target.queryForObject("SELECT COUNT(*) FROM operation_event WHERE request_id=? AND event_type='REPORT_EXPORT_GENERATED'",
            Integer.class, "r08:" + job);
        assertThat(audits).isEqualTo(committed ? 1 : 0);
        var fact = SubmissionJson.object().put("pid", ProcessHandle.current().pid()).put("job", job)
            .put("commitVisible", committed).put("auditCount", audits);
        if (committed) {
            Path home = profile.attempt.resolve("private/restored-bound-wire-commit");
            assertThat(home.resolve("wire-ack-held.json")).isRegularFile();
            assertThat(home.resolve("lose-ack.marker")).doesNotExist();
            assertThat(home.resolve("jdbc-driver-failure.json")).doesNotExist();
            assertThat(home.resolve("jdbc-driver-returned-success.marker")).doesNotExist();
            fact.put("ackStillHeld", true).put("driverFailureAbsent", true);
        }
        fact.set("row", new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(row));
        fact.set("database", new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(target.queryForMap(
            "SELECT @@server_uuid AS uuid,DATABASE() AS schemaName,@@port AS port,@@datadir AS datadir,CURRENT_USER() AS userName,CONNECTION_ID() AS connectionId")));
        save(committed ? "commit-after-observer.json" : "commit-before-observer.json", fact);
        var snapshot = copier.snapshot(target, copier.restored.resolve("storage"));
        save(committed ? "commit-visible-after.json" : "commit-visible-before.json", snapshot);
        return snapshot;
    }

    void refuse() throws Exception {
        var paths = List.of("/auth/csrf", input.exportPath(), view(original), view(job),
            "/exports/" + original + "/file?warehouseId=" + input.warehouse(),
            "/exports/" + job + "/file?warehouseId=" + input.warehouse());
        for (int i = 0; i < paths.size(); i++)
            assertThat(http.get(paths.get(i), 503).path("code").asText())
                .isEqualTo(i == 0 ? "RECOVERY_LEASE_OUTSTANDING" : "RECOVERY_LEASE_FENCED");
    }

    private String view(String id) { return "/exports/" + id + "?warehouseId=" + input.warehouse(); }
    private void save(String name, ObjectNode value) throws Exception {
        Files.writeString(profile.attempt.resolve("private/" + name), value.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }
}
