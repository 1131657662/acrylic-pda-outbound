package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.evidence.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.context.ConfigurableApplicationContext;
import static org.assertj.core.api.Assertions.*;

final class E2aUnsubmittedChecks {
    private final E2aCrossSourceHttp http;
    private final E2aCrossSourceHttpSetup setup;
    private final E2aUnsubmittedPreparation preparation;
    private final ConfigurableApplicationContext context;
    private final UUID verifyKey = UUID.randomUUID();

    E2aUnsubmittedChecks(E2aCrossSourceHttp http, E2aCrossSourceHttpSetup setup,
        E2aUnsubmittedPreparation preparation, ConfigurableApplicationContext context) {
        this.http = http; this.setup = setup; this.preparation = preparation; this.context = context;
    }

    void verify() throws Exception {
        auditUpload();
        uploadWithRollbackChecks();
        var wire = http.wire;
        var accepted = context.getBean(SourceEvidenceIntakeTransactions.class).write(status ->
            context.getBean(SourceEvidenceIntakeBusinessFacts.class).require(setup.fixture.binding(wire.openKey), setup.items(), 5, null, 11));
        assertThat(accepted).containsExactly(E2aCrossSourceFixture.item("SCAN", setup.fixture.ownScan));
        rejectsCorruptCredential(); rejectsCrossWarehouse(); rejectsDamagedPreparation();
        rejectsRawCorruption(); rejectsForeignOwnedCapture();
        newerAuthorizationCannotReplaceOriginal();
        new E2aUnsubmittedNegativeChecks(http, setup, context).verify();
        byte[] original = new E2aIntakeSqlRollback(http).receipt("verify", verifyKey, wire.verify(1));
        assertThat(http.post("verify", verifyKey, wire.verify(1))).isEqualTo(original);
        http.original(verifyKey, original);
        assertThat(SubmissionJson.parse(original).path("status").asText()).isEqualTo("VERIFIED_COMPLETE_AT_STOP");
        assertThat(setup.fixture.jdbc.queryForObject("SELECT receipt_status FROM source_evidence_intake_receipt WHERE original_key=?",
            String.class, Ids.bytes(verifyKey))).isEqualTo("VERIFIED_COMPLETE_AT_STOP");
        assertThat(SubmissionJson.parse(original).path("capabilities").path("localDeletionAllowed").booleanValue()).isFalse();
        database();
    }

    private void uploadWithRollbackChecks() throws Exception {
        var wire = http.wire;
        var raw = new E2aCrossSourceRawHttp(http, setup);
        var rollback = new E2aIntakeSqlRollback(http);
        raw.beforeOpen();
        rollback.receipt("open", wire.openKey, wire.open());
        rollback.raw(raw); raw.upload();
        rollback.receipt("page", wire.pageKey, wire.pageRequest());
        rollback.receipt("stop", wire.stopKey, wire.stop(setup.audit, setup.deviceKey));
    }

    private void rejectsRawCorruption() throws Exception {
        var item = E2aCrossSourceFixture.item("CAPTURE", setup.extraRecords.getFirst());
        Path root = Path.of(context.getEnvironment().getRequiredProperty("outbound.storage"));
        Path path = root.resolve("source-evidence-intake/objects/" + item.itemBytesSha256() + ".bin");
        byte[] original = Files.readAllBytes(path);
        Files.write(path, "damaged original".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try {
            http.reject("verify", verifyKey, http.wire.verify(1), "E2A_RAW_OBJECT_FILE_UNAVAILABLE");
        } finally { Files.write(path, original); }
    }

    private void rejectsForeignOwnedCapture() throws Exception {
        var f = setup.fixture; var capture = setup.extraRecords.getFirst();
        byte[] id = Ids.bytes(SubmissionJson.uuid(capture, "id"));
        f.jdbc.update("INSERT INTO submitted_capture VALUES(?,?,?,?,?,?,?)", id, Ids.bytes(f.priorSubmission),
            f.id("orderId"), f.id("deviceId"), 3, 3, "f".repeat(64));
        try {
            http.reject("verify", verifyKey, http.wire.verify(1), "E2A_OTHER_CAPTURE_NOT_STORED");
        } finally { f.jdbc.update("DELETE FROM submitted_capture WHERE id=?", id); }
    }

    private void auditUpload() throws Exception {
        var body = SubmissionJson.object().put("originDeviceId", setup.fixture.seed.get("deviceId"));
        var events = body.putArray("events"); setup.extraAudits.forEach(events::add); events.add(setup.audit);
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda/audit/events"))
            .header("Authorization", "Bearer " + setup.token).header("X-Server-Recovery-Epoch", "1")
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(E2aCrossSourceFixture.bytes(body))).build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
        Files.write(http.evidence.resolve("audit-upload.json"), response.body(), StandardOpenOption.CREATE_NEW);
        assertThat(response.statusCode()).as(new String(response.body(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(200);
        assertThat(SubmissionJson.parse(response.body()).path("data").path("acknowledgedThrough").asText()).isEqualTo("11");
    }

    private void rejectsCorruptCredential() throws Exception {
        var jdbc = setup.fixture.jdbc; byte[] id = Ids.bytes(preparation.formalAuthorization);
        String original = jdbc.queryForObject("SELECT signed_token FROM offline_authorization WHERE id=?", String.class, id);
        jdbc.update("UPDATE offline_authorization SET signed_token='corrupt' WHERE id=?", id);
        try { rejected(); } finally { jdbc.update("UPDATE offline_authorization SET signed_token=? WHERE id=?", original, id); }
    }

    private void rejectsCrossWarehouse() throws Exception {
        var f = setup.fixture; byte[] id = Ids.bytes(preparation.formalAuthorization);
        String original = f.jdbc.queryForObject("SELECT signed_token FROM offline_authorization WHERE id=?", String.class, id);
        var claims = (com.fasterxml.jackson.databind.node.ObjectNode) SubmissionJson.parse(Base64.getUrlDecoder().decode(original.split("\\.")[1]));
        claims.put("warehouseId", UUID.randomUUID().toString());
        String substituted = context.getBean(com.acrylic.outbound.pda.OfflineSigner.class).sign(claims).token();
        f.jdbc.update("UPDATE offline_authorization SET signed_token=? WHERE id=?", substituted, id);
        try { rejected(); } finally {
            f.jdbc.update("UPDATE offline_authorization SET signed_token=? WHERE id=?", original, id);
        }
    }

    private void rejectsDamagedPreparation() throws Exception {
        var jdbc = setup.fixture.jdbc; byte[] id = Ids.bytes(preparation.temporaryAuthorization);
        byte[] original = jdbc.queryForObject("SELECT compressed FROM temporary_preparation_part WHERE authorization_id=? AND part_name='availability'", byte[].class, id);
        jdbc.update("UPDATE temporary_preparation_part SET compressed=? WHERE authorization_id=? AND part_name='availability'", new byte[]{1, 2}, id);
        try { rejected(); } finally {
            jdbc.update("UPDATE temporary_preparation_part SET compressed=? WHERE authorization_id=? AND part_name='availability'", original, id);
        }
    }

    private void newerAuthorizationCannotReplaceOriginal() {
        var f = setup.fixture;
        f.jdbc.update("""
            INSERT INTO offline_authorization(id,assignment_id,authorization_sequence,user_id,device_id,issued_at,expires_at,key_id,signed_token)
            SELECT ?,assignment_id,1,user_id,device_id,issued_at,expires_at,key_id,'invalid-current-token'
            FROM offline_authorization WHERE id=?
            """, Ids.bytes(UUID.randomUUID()), Ids.bytes(preparation.formalAuthorization));
    }

    private void rejected() throws Exception {
        http.reject("verify", verifyKey, http.wire.verify(1), "E2A_OTHER_UNSUBMITTED_UNVERIFIABLE");
        assertThat(setup.fixture.jdbc.queryForObject("SELECT COUNT(*) FROM source_evidence_set WHERE submission_id=?",
            Integer.class, Ids.bytes(setup.fixture.submission))).isZero();
    }

    private void database() {
        var f = setup.fixture;
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM source_evidence_item WHERE submission_id=?", Integer.class, Ids.bytes(f.submission))).isEqualTo(setup.items().size());
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE bale_id=?", Integer.class, Ids.bytes(f.priorBale))).isZero();
        assertThat(f.jdbc.queryForObject("SELECT state FROM bale_commitment WHERE bale_id=? AND source_scope_id=?", String.class,
            Ids.bytes(f.ownBale), Ids.bytes(f.scope))).isEqualTo("SOURCE_RESERVED");
        for (var record : setup.extraRecords) if (record.has("state"))
            assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM submitted_capture WHERE id=?", Integer.class,
                Ids.bytes(SubmissionJson.uuid(record, "id")))).isZero();
    }
}
