package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoverySourceAuthorityTest {
    @ParameterizedTest @ValueSource(strings = {"FORMAL", "TEMP"})
    void provesHistoricalCredentialAtTheOriginalFreezeTimeWithPublicKeysOnly(String kind) throws Exception {
        var f = new RecoveryAuthorityFixture(kind); var source = f.source.read();

        assertThat(com.acrylic.outbound.submissions.SubmissionJson.canonical(f.reader().verify(source)))
            .isEqualTo(com.acrylic.outbound.submissions.SubmissionJson.canonical(f.claims));
        assertThat(source.ordinaryWritesAllowed()).isFalse(); assertThat(source.localDeletionAllowed()).isFalse();
    }

    @ParameterizedTest @ValueSource(strings = {"assignmentId", "orderId", "sub", "deviceId", "warehouseId"})
    void rejectsAnotherFormalCredentialEvenIfSigned(String field) throws Exception {
        var f = new RecoveryAuthorityFixture("FORMAL"); f.claims.put(field, UUID.randomUUID().toString()); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.source.read())).hasMessage("RECOVERY_SOURCE_ORIGIN_MISMATCH");
    }

    @ParameterizedTest @ValueSource(strings = {"authorizationId", "userId", "deviceId", "warehouseId"})
    void rejectsAnotherTemporaryCredentialEvenIfSigned(String field) throws Exception {
        var f = new RecoveryAuthorityFixture("TEMP"); ((ObjectNode)f.claims.path("identity")).put(field, UUID.randomUUID().toString()); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.source.read())).hasMessage("RECOVERY_SOURCE_ORIGIN_MISMATCH");
    }

    @ParameterizedTest @ValueSource(strings = {"FORMAL", "TEMP"})
    void refusesAWrongSourceEpochDespiteValidSignature(String kind) throws Exception {
        var f = new RecoveryAuthorityFixture(kind);
        var claims = kind.equals("FORMAL") ? f.claims : (ObjectNode)f.claims.path("identity");
        claims.put("serverRecoveryEpoch", "99"); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.source.read())).hasMessage("RECOVERY_SOURCE_ORIGIN_MISMATCH");
    }

    @ParameterizedTest @ValueSource(strings = {"FORMAL", "TEMP"})
    void refusesAnotherPurposeDespiteValidSignature(String kind) throws Exception {
        var f = new RecoveryAuthorityFixture(kind); f.claims.put("scope", "pda:handover"); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.source.read())).hasMessage("RECOVERY_SOURCE_ORIGIN_MISMATCH");
    }

    @Test void refusesFreezeBeforeIssueInsteadOfUsingTodaysCredential() throws Exception {
        var f = new RecoveryAuthorityFixture("FORMAL"); f.closedAt = "2026-09-19T23:59:59Z"; f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.source.read())).hasMessage("RECOVERY_SOURCE_ORIGIN_WINDOW");
    }

    @Test void refusesFreezeAtExpiry() throws Exception {
        var f = new RecoveryAuthorityFixture("TEMP"); f.closedAt = "2026-09-21T00:00:00Z"; f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.source.read())).hasMessage("RECOVERY_SOURCE_ORIGIN_WINDOW");
    }

    @Test void refusesReversedAuthorizationWindow() throws Exception {
        var f = new RecoveryAuthorityFixture("FORMAL"); f.claims.put("exp", f.claims.path("iat").longValue()); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.source.read())).hasMessage("RECOVERY_SOURCE_ORIGIN_WINDOW");
    }

    @ParameterizedTest @ValueSource(strings = {"iat", "exp"})
    void refusesNonIntegralAuthorizationTimes(String field) throws Exception {
        var f = new RecoveryAuthorityFixture("FORMAL"); f.claims.put(field, "1790035200"); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.source.read())).hasMessage("RECOVERY_SOURCE_ORIGIN_WINDOW");
    }

    @ParameterizedTest @ValueSource(strings = {"iss", "aud"})
    void refusesAnotherIssuerOrAudience(String field) throws Exception {
        var f = new RecoveryAuthorityFixture("FORMAL"); f.claims.put(field, "another-service"); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.source.read())).hasMessage("RECOVERY_SOURCE_ORIGIN_INVALID");
    }

    @Test void refusesAKeyOutsideTheExplicitHistoricalTrustSet() throws Exception {
        var f = new RecoveryAuthorityFixture("TEMP"); var untrusted = new RecoveryReceiptFixture();
        var verifier = new RecoverySourceAuthority(new RecoveryReceiptKeys(java.util.List.of(untrusted.pair.getPublic().getEncoded())));

        assertThatThrownBy(() -> verifier.verify(f.source.read())).hasMessage("RECOVERY_RECEIPT_KEY_UNKNOWN");
    }

    @Test void refusesTamperedClaimsWithoutResigning() throws Exception {
        var f = new RecoveryAuthorityFixture("FORMAL"); var origin = com.acrylic.outbound.submissions.SubmissionJson.parse(f.source.read().original("origin"));
        String[] parts = origin.path("offlineToken").textValue().split("\\.");
        f.claims.put("deviceId", UUID.randomUUID().toString());
        parts[1] = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(RecoveryReceiptFixture.bytes(f.claims));
        f.key.object(f.source, "origin", RecoveryReceiptFixture.bytes(com.acrylic.outbound.submissions.SubmissionJson.object()
            .put("offlineToken", String.join(".", parts)))); f.source.publish();

        assertThatThrownBy(() -> f.reader().verify(f.source.read())).hasMessage("RECOVERY_SOURCE_ORIGIN_INVALID");
    }

    @ParameterizedTest @ValueSource(strings = {"FORMAL", "TEMP"})
    void doesNotReuseAnInitialCredentialForAHandoverOrContinuationRound(String kind) throws Exception {
        var f = new RecoveryAuthorityFixture(kind);
        var later = kind.equals("FORMAL") ? RecoverySourceFixture.create() : RecoverySourceFixture.temporary();
        f.key.object(later, "origin", f.source.read().original("origin")); later.publish();

        assertThatThrownBy(() -> f.reader().verify(later.read())).hasMessage("RECOVERY_SOURCE_ORIGIN_ROUND_UNSUPPORTED");
    }

    @ParameterizedTest @ValueSource(strings = {"freezeDeviceId", "assignmentId"})
    void refusesAnOriginalPayloadForAnotherDeviceOrQualification(String field) throws Exception {
        var f = new RecoveryAuthorityFixture("FORMAL");
        var payload = (ObjectNode)com.acrylic.outbound.submissions.SubmissionJson.parse(f.source.read().original("payload"));
        payload.put(field, UUID.randomUUID().toString()); f.key.object(f.source, "payload", RecoveryReceiptFixture.bytes(payload)); f.source.publish();

        assertThatThrownBy(() -> f.reader().verify(f.source.read())).hasMessage("RECOVERY_SOURCE_ORIGIN_PAYLOAD");
    }
}
