package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.ExportStatusHttpInput;
import com.acrylic.outbound.recovery.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Existing export-center filters retain exact results under P5-admitted real HTTP sessions. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aExportStatusReadIT {
    private static final Set<String> SESSION = Set.of("spring_session", "spring_session_attributes");
    private SubmissionChunkFaultState state;
    private Path evidence;
    private ExportStatusHttpInput input;

    @Test void effectiveStatusFiltersRemainReadOnlyAcrossTiedPaginationAndAccessFailures() throws Exception {
        var profile = new E2aCrossSourceProfile(); var raw = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(raw, "34"); E2aV34Seed.seed(raw, profile.attempt.resolve("storage")); profile.migrate(raw);
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var setup = E2aCrossSourceHttpIT.start(profile)) {
            input = ExportStatusHttpInput.prepare((WebApplicationContext) setup);
        }
        evidence = Files.createDirectory(profile.attempt.resolve("export-status"));
        Files.writeString(evidence.resolve("input.json"), new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(input), StandardOpenOption.CREATE_NEW);
        state = new SubmissionChunkFaultState(raw, profile.attempt.resolve("storage"));
        var p5 = new RecoveryRetirementApplicationFixture(profile.attempt);
        var options = new ArrayList<String>(List.of(p5.options())); options.add("--server.servlet.session.cookie.secure=false");
        try (var app = E2aCrossSourceHttpIT.start(profile, options.toArray(String[]::new))) {
            try { consume(); }
            finally { org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(app.getBean(RecoveryOperationRegistry.class).liveRoots()).isZero()); }
        }
        p5.nextProcessStarts();
        Files.writeString(profile.attempt.resolve("test-completed.marker"), "Read-only export status filters and full fixture restoration passed");
    }

    private void consume() throws Exception {
        var owner = wire("owner", input.username());
        unchanged("filters", () -> filters(owner));
        var foreign = wire("same-warehouse-other-actor", input.foreignUsername());
        var anonymous = wire("anonymous", null);
        unchanged("identity-and-cursors", () -> {
            anonymous.get(input.path() + "&status=FAILED", 401);
            owner.get(input.path().replace(input.warehouse(), UUID.randomUUID().toString()) + "&status=FAILED", 404);
            assertThat(foreign.get(input.path() + "&status=FAILED", 200).at("/data/items")).isEmpty();
            foreign.get(input.path() + "&status=FAILED&before=" + input.tasks().getFirst().id(), 404);
            owner.get(input.path() + "&before=" + UUID.randomUUID(), 404);
        });
        unchanged("all-access-mutations-restored", () -> {
            warehouseRemoved(owner); permissionRemoved(owner); sessionRevoked(owner);
        });
    }

    private void filters(RecoveryWorkerHttp http) throws Exception {
        assertThat(pages(http, "")).containsExactlyElementsOf(input.selected(null, null));
        for (String status : List.of("FAILED", "PENDING", "RUNNING", "RETRY", "SUCCEEDED", "EXPIRED", "INVALIDATED")) {
            assertThat(pages(http, "&status=" + status)).as(status).containsExactlyElementsOf(input.selected(status, null));
            for (String format : List.of("XLSX", "PDF"))
                assertThat(pages(http, "&status=" + status + "&format=" + format)).as(status + "/" + format)
                    .containsExactlyElementsOf(input.selected(status, format));
        }
        http.get(input.path() + "&status=UNKNOWN", 400);
    }

    private List<String> pages(RecoveryWorkerHttp http, String filter) throws Exception {
        var ids = new ArrayList<String>(); String cursor = "";
        for (int page = 0; page < 3; page++) {
            var data = http.get(input.path() + filter + cursor, 200).path("data");
            var items = data.path("items"); assertThat(items.size()).isLessThanOrEqualTo(20);
            for (var item : items) ids.add(item.path("id").asText());
            if (data.path("nextCursor").isNull()) { assertThat(ids).doesNotHaveDuplicates(); return ids; }
            assertThat(items).hasSize(20);
            assertThat(data.path("nextCursor").asText()).isEqualTo(items.get(19).path("id").asText());
            cursor = "&before=" + data.path("nextCursor").asText();
        }
        throw new AssertionError("Synthetic result set should fit in two pages");
    }

    private void warehouseRemoved(RecoveryWorkerHttp http) throws Exception {
        assertThat(state.raw.update("DELETE FROM user_warehouse WHERE user_id=UUID_TO_BIN(?) AND warehouse_id=UUID_TO_BIN(?)",
            input.reviewer(), input.warehouse())).isOne();
        try { unchanged("warehouse-removed", () -> http.get(input.path() + "&status=FAILED", 404)); }
        finally { assertThat(state.raw.update("INSERT INTO user_warehouse VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?))",
            input.reviewer(), input.warehouse())).isOne(); }
    }

    private void permissionRemoved(RecoveryWorkerHttp http) throws Exception {
        assertThat(state.raw.update("DELETE FROM user_role WHERE user_id=UUID_TO_BIN(?) AND role_id=UUID_TO_BIN(?)",
            input.reviewer(), input.role())).isOne();
        try { unchanged("permission-removed", () -> http.get(input.path() + "&status=FAILED", 403)); }
        finally { assertThat(state.raw.update("INSERT INTO user_role VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?))",
            input.reviewer(), input.role())).isOne(); }
    }

    private void sessionRevoked(RecoveryWorkerHttp http) throws Exception {
        state.raw.update("UPDATE app_user SET auth_version=auth_version+1 WHERE id=UUID_TO_BIN(?)", input.reviewer());
        try { unchanged("session-revoked", () -> http.get(input.path() + "&status=FAILED", 401)); }
        finally { state.raw.update("UPDATE app_user SET auth_version=auth_version-1 WHERE id=UUID_TO_BIN(?)", input.reviewer()); }
    }

    private RecoveryWorkerHttp wire(String label, String username) throws Exception {
        var http = new RecoveryWorkerHttp(evidence.resolve(label));
        if (username != null) http.login(username);
        return http;
    }

    private void unchanged(String label, Checked action) throws Exception {
        var db = state.database(SESSION); var files = state.files();
        save(label + "-database-before", db); save(label + "-files-before", files);
        try { action.run(); }
        finally {
            var after = state.database(SESSION); var afterFiles = state.files();
            save(label + "-database-after", after); save(label + "-files-after", afterFiles);
            assertThat(after).isEqualTo(db); assertThat(afterFiles).isEqualTo(files);
        }
    }

    private void save(String name, com.fasterxml.jackson.databind.JsonNode value) throws Exception {
        Files.writeString(evidence.resolve(name + ".json"), value.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }

    private interface Checked { void run() throws Exception; }
}
