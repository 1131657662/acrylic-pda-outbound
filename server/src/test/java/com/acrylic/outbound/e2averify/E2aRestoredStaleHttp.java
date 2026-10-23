package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.RecoveryExportInput;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Preserve a third real HTTP PENDING snapshot and old authenticated client refusals. */
final class E2aRestoredStaleHttp {
    private final E2aCrossSourceProfile profile;
    private final RecoveryExportInput input;
    private final RecoveryWorkerHttp http;
    private final String original;
    private String pending;

    E2aRestoredStaleHttp(E2aCrossSourceProfile profile, RecoveryExportInput input) throws Exception {
        this(profile, input, SubmissionJson.parse(Files.readString(profile.attempt.resolve("pending-xlsx.json"))).path("id").asText());
    }

    E2aRestoredStaleHttp(E2aCrossSourceProfile profile, RecoveryExportInput input, String original) throws Exception {
        this.profile = profile; this.input = input;
        this.original = original;
        http = new RecoveryWorkerHttp(profile.attempt.resolve("private/http-stale"));
        http.login(input.username());
    }

    void pending(JdbcTemplate target) throws Exception {
        pending(target, 3);
    }

    void pending(JdbcTemplate target, int expectedJobs) throws Exception {
        String key = UUID.randomUUID().toString();
        pending = http.create(input.exportPath(), "XLSX", key, 200).at("/data/id").asText();
        var view = http.get(view(pending), 200).path("data");
        assertThat(view.path("status").asText()).isEqualTo("PENDING");
        assertThat(view.path("attempts").asInt()).isZero();
        assertThat(view.path("downloadable").asBoolean()).isFalse();
        var record = SubmissionJson.object().put("id", pending).put("key", key); record.set("view", view);
        record.set("payload", SubmissionJson.parse(target.queryForObject("SELECT payload FROM background_job WHERE id=UUID_TO_BIN(?)", String.class, pending)));
        Files.writeString(profile.attempt.resolve("stale-pending.json"), record.toPrettyString(), StandardOpenOption.CREATE_NEW);
        assertThat(target.queryForObject("SELECT COUNT(*) FROM background_job", Integer.class)).isEqualTo(expectedJobs);
    }

    void refuse() throws Exception { refuse("CONTROL_INSTANCE_STALE"); }

    void refuse(String expected) throws Exception {
        for (String path : List.of("/auth/csrf", input.exportPath(), view(original), view(pending),
                "/exports/" + original + "/file?warehouseId=" + input.warehouse(),
                "/exports/" + pending + "/file?warehouseId=" + input.warehouse()))
            assertThat(http.get(path, 503).path("code").asText()).isEqualTo(expected);
    }

    void refuseOrphan() throws Exception {
        var paths = List.of("/auth/csrf", input.exportPath(), view(original), view(pending),
            "/exports/" + original + "/file?warehouseId=" + input.warehouse(),
            "/exports/" + pending + "/file?warehouseId=" + input.warehouse());
        for (int i = 0; i < paths.size(); i++)
            assertThat(http.get(paths.get(i), 503).path("code").asText())
                .isEqualTo(i == 0 ? "RECOVERY_LEASE_OUTSTANDING" : "RECOVERY_LEASE_FENCED");
    }

    private String view(String id) { return "/exports/" + id + "?warehouseId=" + input.warehouse(); }
}
