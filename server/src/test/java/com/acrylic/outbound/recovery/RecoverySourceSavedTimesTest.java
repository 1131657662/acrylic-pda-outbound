package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoverySourceSavedTimesTest {
    @ParameterizedTest @ValueSource(strings = {"FORMAL", "TEMP"})
    void preservesValidOriginalSavesDespiteClosingLongAfterExpiry(String kind) throws Exception {
        var f = new RecoveryAuthorityFixture(kind); f.closedAt = "2026-10-09T00:00:00Z"; f.publish();

        assertThat(f.reader().verify(f.source.read()).path("jti").asText()).isEqualTo(f.claims.path("jti").asText());
    }

    @ParameterizedTest @ValueSource(strings = {"FORMAL", "TEMP"})
    void rejectsSavingAtTheOriginalExpiry(String kind) throws Exception {
        var f = new RecoveryAuthorityFixture(kind); f.savedAt = "2026-09-21T00:00:00Z";
        f.closedAt = "2026-10-09T00:00:00Z"; f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.source.read())).hasMessage("RECOVERY_SOURCE_ORIGIN_WINDOW");
    }

    @ParameterizedTest @ValueSource(strings = {"FORMAL", "TEMP"})
    void rejectsConfirmingBeforeTheOriginalIssue(String kind) throws Exception {
        var f = new RecoveryAuthorityFixture(kind); f.confirmedAt = "2026-09-19T23:59:59Z"; f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.source.read())).hasMessage("RECOVERY_SOURCE_ORIGIN_WINDOW");
    }

    @ParameterizedTest @ValueSource(strings = {"FORMAL", "TEMP"})
    void rejectsSavingBeforeTheOriginalConfirmation(String kind) throws Exception {
        var f = new RecoveryAuthorityFixture(kind); f.savedAt = "2026-09-20T00:29:59Z"; f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.source.read())).hasMessage("RECOVERY_SOURCE_ORIGIN_WINDOW");
    }

    @Test void doesNotAcceptATodaysCredentialForAnOldCapture() throws Exception {
        var f = new RecoveryAuthorityFixture("FORMAL"); var body = body(f);
        ((ObjectNode)body.at("/captures/0")).put("authorizationId", UUID.randomUUID().toString()); publish(f, body);

        assertThatThrownBy(() -> f.reader().verify(f.source.read())).hasMessage("RECOVERY_SOURCE_CREDENTIAL_MISSING");
    }

    @ParameterizedTest @ValueSource(strings = {"originDeviceId", "operatorId", "assignmentId", "orderId", "warehouseId"})
    void rejectsAChangedOriginalCaptureIdentity(String field) throws Exception {
        var f = new RecoveryAuthorityFixture("FORMAL"); var body = body(f);
        ((ObjectNode)body.at("/captures/0")).put(field, UUID.randomUUID().toString()); publish(f, body);

        assertThatThrownBy(() -> f.reader().verify(f.source.read())).hasMessage("RECOVERY_SOURCE_CAPTURE_ORIGIN");
    }

    @Test void rejectsACommittedCaptureWithoutItsOriginalSave() throws Exception {
        var f = new RecoveryAuthorityFixture("TEMP"); var body = body(f); body.putArray("scans"); publish(f, body);

        assertThatThrownBy(() -> f.reader().verify(f.source.read())).hasMessage("RECOVERY_SOURCE_SAVE_MISSING");
    }

    @ParameterizedTest @ValueSource(strings = {"assignmentEpoch", "snapshotId", "orderRevision"})
    void rejectsCaptureMetadataDifferentFromTheOriginalSignedFormalCredential(String field) throws Exception {
        var f = new RecoveryAuthorityFixture("FORMAL"); var body = body(f); var capture = (ObjectNode)body.at("/captures/0");
        if (field.equals("orderRevision")) capture.put(field, 2);
        else capture.put(field, field.equals("snapshotId") ? UUID.randomUUID().toString() : "99");
        publish(f, body);

        assertThatThrownBy(() -> f.reader().verify(f.source.read())).hasMessage("RECOVERY_SOURCE_CAPTURE_CREDENTIAL");
    }

    @Test void rejectsTheWrongTemporaryCredentialEpoch() throws Exception {
        var f = new RecoveryAuthorityFixture("TEMP"); var body = body(f);
        ((ObjectNode)body.at("/captures/0")).put("authorizationEpoch", "99"); publish(f, body);

        assertThatThrownBy(() -> f.reader().verify(f.source.read())).hasMessage("RECOVERY_SOURCE_CAPTURE_CREDENTIAL");
    }

    @Test void rejectsASaveForANonCommittedCapture() throws Exception {
        var f = new RecoveryAuthorityFixture("TEMP"); var body = body(f);
        ((ObjectNode)body.at("/captures/0")).put("state", "INVALID"); publish(f, body);

        assertThatThrownBy(() -> f.reader().verify(f.source.read())).hasMessage("RECOVERY_SOURCE_SAVE_BINDING");
    }

    @Test void rejectsDuplicatingASaveUnderTheOriginalCaptureId() throws Exception {
        var f = new RecoveryAuthorityFixture("FORMAL"); var body = body(f);
        body.withArray("scans").add(body.at("/scans/0").deepCopy()); publish(f, body);

        assertThatThrownBy(() -> f.reader().verify(f.source.read())).hasMessage("RECOVERY_SOURCE_SAVE_BINDING");
    }

    private ObjectNode body(RecoveryAuthorityFixture f) { return (ObjectNode)SubmissionJson.parse(f.source.read().original("payload")); }
    private void publish(RecoveryAuthorityFixture f, ObjectNode body) throws Exception {
        f.key.object(f.source, "payload", RecoveryReceiptFixture.bytes(body)); f.source.publish();
    }
}
