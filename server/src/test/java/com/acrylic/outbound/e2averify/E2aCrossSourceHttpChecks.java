package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

final class E2aCrossSourceHttpChecks {
    private E2aCrossSourceHttpChecks() { }

    static void verify(E2aCrossSourceHttp http, E2aCrossSourceHttpSetup setup) throws Exception {
        var wire = http.wire;
        http.unauthorized();
        var raw = new E2aCrossSourceRawHttp(http, setup);
        raw.beforeOpen();
        byte[] open = http.post("open", wire.openKey, wire.open());
        assertThat(http.post("open", wire.openKey, wire.open())).isEqualTo(open);
        http.original(wire.openKey, open);
        raw.upload();
        byte[] page = http.post("page", wire.pageKey, wire.pageRequest());
        assertThat(http.post("page", wire.pageKey, wire.pageRequest())).isEqualTo(page);
        http.original(wire.pageKey, page);
        var stopBody = wire.stop(setup.audit, setup.deviceKey);
        byte[] stop = http.post("stop", wire.stopKey, stopBody);
        assertThat(http.post("stop", wire.stopKey, stopBody)).isEqualTo(stop);
        http.original(wire.stopKey, stop);
        verifyFacts(http);
    }

    private static void verifyFacts(E2aCrossSourceHttp http) throws Exception {
        var wire = http.wire; var f = wire.fixture;
        UUID verifyKey = UUID.randomUUID();
        byte[] captureId = Ids.bytes(SubmissionJson.uuid(f.earlier, "id"));
        f.jdbc.update("UPDATE submitted_capture SET record_sha256=? WHERE id=?", "f".repeat(64), captureId);
        try {
            http.reject("verify", verifyKey, wire.verify(1), "E2A_OTHER_CAPTURE_NOT_STORED");
            assertNoSetOrReservation(f);
        } finally {
            f.jdbc.update("UPDATE submitted_capture SET record_sha256=? WHERE id=?", SubmissionJson.sha(f.earlier), captureId);
        }
        byte[] verified = http.post("verify", verifyKey, wire.verify(1));
        assertThat(http.post("verify", verifyKey, wire.verify(1))).isEqualTo(verified);
        http.original(verifyKey, verified);
        assertThat(SubmissionJson.parse(verified).path("status").asText()).isEqualTo("PARTIAL");
        assertThat(SubmissionJson.parse(verified).path("capabilities").path("localDeletionAllowed").booleanValue()).isFalse();
        assertReservations(f);
        http.reject("verify", verifyKey, wire.verify(2), "E2A_INTAKE_CONFLICT");
    }

    private static void assertNoSetOrReservation(E2aCrossSourceFixture f) {
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM source_evidence_set WHERE submission_id=?", Integer.class,
            Ids.bytes(f.submission))).isZero();
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE bale_id IN (?,?)", Integer.class,
            Ids.bytes(f.ownBale), Ids.bytes(f.priorBale))).isZero();
    }

    private static void assertReservations(E2aCrossSourceFixture f) {
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM source_evidence_item WHERE submission_id=?", Integer.class,
            Ids.bytes(f.submission))).isEqualTo(5);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE bale_id=?", Integer.class,
            Ids.bytes(f.priorBale))).isZero();
        assertThat(f.jdbc.queryForObject("SELECT state FROM bale_commitment WHERE bale_id=? AND source_scope_id=?", String.class,
            Ids.bytes(f.ownBale), Ids.bytes(f.scope))).isEqualTo("SOURCE_RESERVED");
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM source_evidence_verification WHERE submission_id=?", Integer.class,
            Ids.bytes(f.submission))).isEqualTo(1);
    }
}
