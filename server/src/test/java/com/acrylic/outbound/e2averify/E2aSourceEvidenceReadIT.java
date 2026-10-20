package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.SourceEvidenceHttpInput;
import com.acrylic.outbound.recovery.RecoveryRetirementApplicationFixture;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Existing TEMP original-evidence GETs remain read-only under admitted P5 TCP sessions. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aSourceEvidenceReadIT {
    private Path evidence;
    private SubmissionChunkFaultState state;
    private static final Set<String> SESSION = Set.of("spring_session", "spring_session_attributes");
    private static final List<String> SUFFIXES = List.of("", "/payload", "/chunks/0");

    @Test void originalPayloadAndPartialChunksRemainReadOnlyAcrossAccessAndIntegrityFailures() throws Exception {
        var profile = new E2aCrossSourceProfile(); var raw = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(raw, "34"); E2aV34Seed.seed(raw, profile.attempt.resolve("storage")); profile.migrate(raw);
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        var input = new ArrayList<SourceEvidenceHttpInput>();
        try (var setup = E2aCrossSourceHttpIT.start(profile)) {
            input.add(SourceEvidenceHttpInput.prepare((WebApplicationContext) setup, true));
            input.add(SourceEvidenceHttpInput.prepare((WebApplicationContext) setup, false));
        }
        evidence = Files.createDirectory(profile.attempt.resolve("source-evidence"));
        state = new SubmissionChunkFaultState(raw, profile.attempt.resolve("storage"));
        saveInputs(input); var p5 = new RecoveryRetirementApplicationFixture(profile.attempt);
        var options = new ArrayList<String>(List.of(p5.options()));
        options.add("--server.servlet.session.cookie.secure=false");
        try (var app = E2aCrossSourceHttpIT.start(profile, options.toArray(String[]::new))) {
            try { consume(input); }
            finally {
                org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(15)).untilAsserted(() ->
                    assertThat(app.getBean(com.acrylic.outbound.recovery.RecoveryOperationRegistry.class).liveRoots()).isZero());
            }
        }
        p5.nextProcessStarts();
        Files.writeString(profile.attempt.resolve("test-completed.marker"), "Read-only source evidence TCP and original preservation passed");
    }

    private void consume(List<SourceEvidenceHttpInput> input) throws Exception {
        var accepted = wire("accepted", input.getFirst().username());
        var partial = wire("partial", input.getLast().username());
        positive(input.getFirst(), accepted); positive(input.getLast(), partial);
        integrity(input.getFirst(), accepted); integrity(input.getLast(), partial);
        accessFailures(input.getFirst(), accepted, partial);
    }

    private void saveInputs(List<SourceEvidenceHttpInput> inputs) throws Exception {
        for (var input : inputs) {
            String label = input.accepted() ? "accepted" : "partial";
            save(label + "-input", input.evidence());
            Files.write(evidence.resolve(label + "-original.bin"), input.original(), StandardOpenOption.CREATE_NEW);
        }
    }

    private void positive(SourceEvidenceHttpInput input, SourceEvidenceReadHttp http) throws Exception {
        unchanged(input.accepted() ? "accepted-positive" : "partial-positive", () -> {
            var view = http.detail(input.path(""), 200);
            assertThat(view.at("/terminalProof/verification").asText()).isEqualTo("VERIFIED");
            assertThat(view.at("/terminalProof/originalStoredReceiptUtf8").asText()).isEqualTo(input.receipt());
            assertThat(view.at("/receivedPayload/received").asBoolean()).isEqualTo(input.accepted());
            assertCapabilitiesFalse(view);
            if (input.accepted()) {
                assertThat(http.get(input.path("/payload"), 200)).isEqualTo(input.original());
                http.get(input.path("/chunks/0"), 404);
                assertThat(view.at("/mapping/canonicalOrderId").isTextual()).isTrue();
            } else {
                assertThat(http.get(input.path("/chunks/0"), 200)).isEqualTo(Arrays.copyOfRange(input.original(), 0, 262144));
                http.get(input.path("/payload"), 404); http.get(input.path("/chunks/1"), 404);
                assertThat(view.at("/mapping/canonicalOrderId").isNull()).isTrue();
                assertThat(view.at("/declaredUpload/receivedChunks")).hasSize(1);
            }
        });
    }

    private void accessFailures(SourceEvidenceHttpInput input, SourceEvidenceReadHttp owner, SourceEvidenceReadHttp foreign) throws Exception {
        var anonymous = wire("anonymous", null);
        unchanged("anonymous-cross-warehouse", () -> { reject(anonymous, input, 401); reject(foreign, input, 404); });
        unchanged("wrong-original-identity", () -> {
            for (String suffix : SUFFIXES) {
                String path = input.path(suffix);
                owner.get(path.replace(input.creator(), UUID.randomUUID().toString()), 404);
                owner.get(path.replace(input.client(), UUID.randomUUID().toString()), 404);
                owner.get(path.replace("workRound=1", "workRound=2"), 404);
            }
        });
        unchanged("access-mutations-restored", () -> accessMutations(input, owner));
    }

    private void accessMutations(SourceEvidenceHttpInput input, SourceEvidenceReadHttp owner) throws Exception {
        warehouseGrantRemoved(input, owner);
        mutateAccess(input, owner, "permission-removed", 403,
            "DELETE FROM user_role WHERE user_id=UUID_TO_BIN(?)",
            "INSERT INTO user_role SELECT UUID_TO_BIN(?),id FROM role WHERE code='TEMPORARY_ORDER_CONFIRMER'");
        mutateAccess(input, owner, "session-revoked", 401,
            "UPDATE app_user SET auth_version=auth_version+1 WHERE id=UUID_TO_BIN(?)",
            "UPDATE app_user SET auth_version=auth_version-1 WHERE id=UUID_TO_BIN(?)");
    }

    private void warehouseGrantRemoved(SourceEvidenceHttpInput input, SourceEvidenceReadHttp owner) throws Exception {
        String delete = "DELETE FROM user_warehouse WHERE user_id=UUID_TO_BIN(?) AND warehouse_id=UUID_TO_BIN(?)";
        assertThat(state.raw.update(delete, input.reviewer(), input.warehouse())).isOne();
        try { unchanged("warehouse-grant-removed", () -> reject(owner, input, 404)); }
        finally {
            assertThat(state.raw.update("INSERT INTO user_warehouse VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?))",
                input.reviewer(), input.warehouse())).isOne();
        }
    }

    private void mutateAccess(SourceEvidenceHttpInput input, SourceEvidenceReadHttp http, String name, int status, String change, String restore) throws Exception {
        state.raw.update(change, input.reviewer());
        try { unchanged(name, () -> reject(http, input, status)); }
        finally { state.raw.update(restore, input.reviewer()); }
    }

    private void integrity(SourceEvidenceHttpInput input, SourceEvidenceReadHttp http) throws Exception {
        byte[] original = input.accepted() ? input.original() : Arrays.copyOfRange(input.original(), 0, 262144);
        String hash = com.acrylic.outbound.inventory.ImportFiles.sha256(original);
        Path file = Path.of(System.getenv("E2A_CROSS_ATTEMPT"), "storage", "submissions", hash + ".json");
        assertThat(Files.readAllBytes(file)).isEqualTo(original);
        byte[] damaged = original.clone(); damaged[0] ^= 1; Files.write(file, damaged);
        String label = input.accepted() ? "payload" : "chunk";
        try {
            unchanged(label + "-damaged", () -> {
                http.get(input.path(""), 409);
                http.get(input.path(input.accepted() ? "/payload" : "/chunks/0"), 409);
                assertThat(Files.readAllBytes(file)).isEqualTo(damaged);
            });
        } finally { Files.write(file, original); }
        unchanged(label + "-restored", () -> http.detail(input.path(""), 200));
    }

    private void reject(SourceEvidenceReadHttp http, SourceEvidenceHttpInput input, int status) throws Exception {
        for (String suffix : SUFFIXES) http.get(input.path(suffix), status);
    }

    private void unchanged(String label, Checked action) throws Exception {
        var database = state.database(SESSION); var files = state.files();
        save(label + "-database-before", database); save(label + "-files-before", files);
        action.run();
        var after = state.database(SESSION); var afterFiles = state.files();
        save(label + "-database-after", after); save(label + "-files-after", afterFiles);
        assertThat(after).isEqualTo(database); assertThat(afterFiles).isEqualTo(files);
    }

    private void assertCapabilitiesFalse(JsonNode view) {
        for (String key : List.of("writeAllowed", "writeConfirmationAvailable", "releaseAllowed", "mayResumeScanning",
                "stockReleaseAllowed", "localDeletionAllowed", "evidenceUploadAllowed"))
            assertThat(view.at("/currentAssessment/" + key).isBoolean() && !view.at("/currentAssessment/" + key).asBoolean()).as(key).isTrue();
    }

    private SourceEvidenceReadHttp wire(String label, String username) throws Exception {
        return new SourceEvidenceReadHttp(evidence.resolve(label), username);
    }

    private void save(String name, JsonNode value) throws Exception {
        Files.writeString(evidence.resolve(name + ".json"), value.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }

    private interface Checked { void run() throws Exception; }
}
