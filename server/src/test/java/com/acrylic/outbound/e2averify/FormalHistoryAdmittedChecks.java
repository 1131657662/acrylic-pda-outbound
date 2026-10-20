package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Negative consumers alter only restored fixture access rows or copied files, always restoring original bytes. */
final class FormalHistoryAdmittedChecks {
    private static final Set<String> SESSION = Set.of("spring_session", "spring_session_attributes");
    private final FormalHistoryAdmittedInput input;
    private final SubmissionChunkFaultState state;
    private final Path evidence;

    FormalHistoryAdmittedChecks(FormalHistoryAdmittedInput input, SubmissionChunkFaultState state, Path evidence) {
        this.input = input; this.state = state; this.evidence = evidence;
    }

    void application(byte[] original) throws Exception {
        var proof = SubmissionJson.parse(original); var receipt = proof.at("/application/receipt");
        if (!receipt.hasNonNull("signedApplication")) return;
        assertThat(receipt.path("status").asText()).isEqualTo("DISPOSITIONS_APPLIED");
        assertThat(receipt.path("signedApplication").asText()).isNotBlank();
        var rows = input.raw.query("SELECT receipt_bytes,receipt_sha256,payload_sha256 FROM formal_disposition_application WHERE submission_id=UUID_TO_BIN(?)",
            (rs, n) -> new Application(rs.getBytes(1), rs.getString(2), rs.getString(3)),
            proof.at("/application/body/submissionId").asText());
        assertThat(rows).hasSize(1); var row = rows.getFirst();
        assertThat(ImportFiles.sha256(row.bytes())).isEqualTo(row.receiptHash());
        assertThat(SubmissionJson.parse(row.bytes())).isEqualTo(receipt);
        assertThat(row.payloadHash()).isEqualTo(proof.at("/application/payloadSha256").asText());
        for (String flag : List.of("localDeletionAllowed", "mayResumeScanning", "stockReleased"))
            assertThat(receipt.path(flag).isBoolean() && !receipt.path(flag).asBoolean()).isTrue();
    }

    void deniedReads(CorrectionApprovalAdmittedHttp http) throws Exception {
        revoked(http, "user_role"); revoked(http, "user_warehouse"); corrupted(http);
    }

    private void revoked(CorrectionApprovalAdmittedHttp http, String table) throws Exception {
        String column = switch (table) { case "user_role" -> "role_id"; case "user_warehouse" -> "warehouse_id";
            default -> throw new IllegalArgumentException("Only isolated access fixtures"); };
        byte[] user = input.raw.queryForObject("SELECT id FROM app_user WHERE username=?", byte[].class,
            input.browser.path("checkerUsername").asText());
        var rows = input.raw.queryForList("SELECT " + column + " FROM " + table + " WHERE user_id=?", byte[].class, user);
        assertThat(rows).isNotEmpty(); var before = state.database(SESSION); var files = state.files();
        try {
            assertThat(input.raw.update("DELETE FROM " + table + " WHERE user_id=?", user)).isEqualTo(rows.size());
            var revoked = state.database(SESSION);
            reject(http, table.equals("user_role") ? 403 : 404);
            assertThat(state.database(SESSION)).isEqualTo(revoked); assertThat(state.files()).isEqualTo(files);
        } finally {
            for (byte[] value : rows)
                assertThat(input.raw.update("INSERT INTO " + table + "(user_id," + column + ") VALUES (?,?)", user, value)).isEqualTo(1);
            conserved(table, before, files);
        }
    }

    private void corrupted(CorrectionApprovalAdmittedHttp http) throws Exception {
        var proof = SubmissionJson.parse(Files.readAllBytes(input.profile.attempt.resolve("historical-original-formal-proof.json")));
        String hash = proof.at("/application/payloadSha256").asText(); assertThat(hash).matches("[a-f0-9]{64}");
        Path file = input.profile.attempt.resolve("storage/submissions/" + hash + ".json");
        byte[] original = Files.readAllBytes(file); assertThat(ImportFiles.sha256(original)).isEqualTo(hash);
        Files.write(evidence.resolve("copied-payload-before.bin"), original, StandardOpenOption.CREATE_NEW);
        var before = state.database(SESSION); var files = state.files();
        try {
            byte[] invalid = original.clone(); invalid[0] ^= 1; Files.write(file, invalid);
            var damaged = state.files(); reject(http, 409);
            assertThat(state.database(SESSION)).isEqualTo(before); assertThat(state.files()).isEqualTo(damaged);
        } finally { Files.write(file, original); conserved("corrupted-payload", before, files); }
    }

    private void reject(CorrectionApprovalAdmittedHttp http, int status) throws Exception {
        String code = switch (status) { case 403 -> "CORRECTION_PERMISSION_DENIED";
            case 404 -> "CORRECTION_DOCUMENT_NOT_FOUND"; case 409 -> "CORRECTION_RECEIPT_INTEGRITY_FAILURE";
            default -> throw new IllegalArgumentException(); };
        for (String suffix : List.of("context", "original-receipt")) {
            var response = new String(http.get(input.route(suffix), status), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(response).contains(code).doesNotContain("signedApplication", "FORMAL_RECOVERY_APPLICATION_ORIGINALS");
        }
    }

    private void conserved(String label, JsonNode before, JsonNode files) throws Exception {
        var after = state.database(SESSION); var afterFiles = state.files();
        Files.writeString(evidence.resolve(label + "-restored-database.json"), after.toPrettyString(), StandardOpenOption.CREATE_NEW);
        Files.writeString(evidence.resolve(label + "-restored-files.json"), afterFiles.toPrettyString(), StandardOpenOption.CREATE_NEW);
        assertThat(after).isEqualTo(before); assertThat(afterFiles).isEqualTo(files);
    }

    private record Application(byte[] bytes, String receiptHash, String payloadHash) { }
}
