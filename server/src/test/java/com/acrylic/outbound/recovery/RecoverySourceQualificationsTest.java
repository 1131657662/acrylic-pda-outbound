package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RecoverySourceQualificationsTest {
    @Test void usesEachFormalRenewalsOriginalWindowInsteadOfTheLastCredentialForEverySave() throws Exception {
        var original = new RecoveryAuthorityFixture("FORMAL");
        var renewal = new RecoveryAuthorityFixture(RecoverySourceFixture.credentialCopy(original.source));
        laterWindow(renewal); combine(renewal, original);

        verifier(original, renewal).verify(renewal.source.read(), catalog(original, renewal));

        assertThatThrownBy(() -> renewal.reader().verify(renewal.source.read())).hasMessage("RECOVERY_SOURCE_CREDENTIAL_MISSING");
    }

    @Test void keepsMixedFormalAndTemporaryCapturesOnTheirOwnHistoricalCredentials() throws Exception {
        var current = new RecoveryAuthorityFixture("FORMAL");
        var prior = new RecoveryAuthorityFixture(RecoverySourceFixture.related(current.source, "TEMP"));
        laterWindow(current); combine(current, prior);

        verifier(prior, current).verify(current.source.read(), catalog(prior, current));

        assertThatThrownBy(() -> verifier(prior, current).verify(current.source.read(), catalog(current)))
            .hasMessage("RECOVERY_SOURCE_CREDENTIAL_MISSING");
    }

    @Test void rejectsAnExpiredPriorSaveEvenWhenTheCurrentCredentialIsValidAtThatTime() throws Exception {
        var prior = new RecoveryAuthorityFixture("FORMAL");
        prior.confirmedAt = "2026-09-21T00:10:00Z"; prior.savedAt = "2026-09-21T00:11:00Z";
        prior.closedAt = "2026-09-22T00:00:00Z"; prior.publish();
        var current = new RecoveryAuthorityFixture(RecoverySourceFixture.credentialCopy(prior.source));
        laterWindow(current); combine(current, prior);

        assertThatThrownBy(() -> verifier(prior, current).verify(current.source.read(), catalog(prior, current)))
            .hasMessage("RECOVERY_SOURCE_ORIGIN_WINDOW");
    }

    @Test void rejectsAnOriginalCredentialCataloguedUnderAnotherAuthorizationId() throws Exception {
        var current = new RecoveryAuthorityFixture("FORMAL");

        assertThatThrownBy(() -> verifier(current).verify(current.source.read(), Map.of(UUID.randomUUID(), current.source.read())))
            .hasMessage("RECOVERY_SOURCE_CREDENTIAL_ID");
    }

    @Test void rejectsACredentialFromAnotherWarehouse() throws Exception {
        var current = new RecoveryAuthorityFixture("FORMAL"); var foreign = new RecoveryAuthorityFixture("TEMP");

        assertThatThrownBy(() -> verifier(current, foreign).verify(current.source.read(), catalog(current, foreign)))
            .hasMessage("RECOVERY_SOURCE_CREDENTIAL_WAREHOUSE");
    }

    @Test void rejectsAPinFromAnotherClosedRecoverySession() throws Exception {
        var current = new RecoveryAuthorityFixture("TEMP"); var other = RecoverySourceFixture.initial("TEMP");
        var pinned = current.source.read(); var objects = new HashMap<String, byte[]>();
        RecoverySourceManifest.OBJECTS.forEach(kind -> objects.put(kind, pinned.original(kind)));
        var wrong = new RecoveryPinnedSource(pinned.identity(), other.control.read(), pinned.manifestSha256(), objects);

        assertThatThrownBy(() -> verifier(current).verify(pinned, Map.of(UUID.fromString(current.claims.path("jti").asText()), wrong)))
            .hasMessage("RECOVERY_SOURCE_CREDENTIAL_CONTROL");
    }

    @Test void doesNotTreatUnrelatedHistoricalSourcesAsTheCurrentQualification() throws Exception {
        var current = new RecoveryAuthorityFixture("FORMAL");
        var other = new RecoveryAuthorityFixture(RecoverySourceFixture.related(current.source, "TEMP"));

        assertThatThrownBy(() -> verifier(current, other).verify(current.source.read(), catalog(other)))
            .hasMessage("RECOVERY_SOURCE_CREDENTIAL_CURRENT_MISSING");
    }

    @Test void doesNotSubstituteAnotherPinOfTheSameAssignmentForTheCurrentOriginal() throws Exception {
        var current = new RecoveryAuthorityFixture("FORMAL");
        var another = new RecoveryAuthorityFixture(RecoverySourceFixture.credentialCopy(current.source));

        assertThatThrownBy(() -> verifier(current, another).verify(current.source.read(), catalog(another)))
            .hasMessage("RECOVERY_SOURCE_CREDENTIAL_CURRENT_MISSING");
    }

    private void laterWindow(RecoveryAuthorityFixture fixture) throws Exception {
        fixture.claims.put("iat", Instant.parse("2026-09-21T00:00:00Z").getEpochSecond())
            .put("exp", Instant.parse("2026-09-22T00:00:00Z").getEpochSecond());
        fixture.confirmedAt = "2026-09-21T00:30:00Z"; fixture.savedAt = "2026-09-21T00:31:00Z";
        fixture.closedAt = "2026-10-09T00:00:00Z"; fixture.publish();
    }

    private void combine(RecoveryAuthorityFixture current, RecoveryAuthorityFixture prior) throws Exception {
        var body = (ObjectNode)SubmissionJson.parse(current.source.read().original("payload"));
        var previous = SubmissionJson.parse(prior.source.read().original("payload"));
        for (String key : List.of("captures", "scans")) {
            var original = body.withArray(key).get(0).deepCopy(); ((ObjectNode)original).put("orderScanSeq", "2");
            body.putArray(key).add(previous.path(key).get(0).deepCopy()).add(original);
        }
        current.key.object(current.source, "payload", RecoveryReceiptFixture.bytes(body)); current.source.publish();
    }

    private Map<UUID, RecoveryPinnedSource> catalog(RecoveryAuthorityFixture... originals) {
        var result = new LinkedHashMap<UUID, RecoveryPinnedSource>();
        for (var original : originals) result.put(UUID.fromString(original.claims.path("jti").asText()), original.source.read());
        return result;
    }

    private RecoverySourceQualifications verifier(RecoveryAuthorityFixture... originals) {
        return new RecoverySourceQualifications(new RecoveryReceiptKeys(Arrays.stream(originals)
            .map(original -> original.key.pair.getPublic().getEncoded()).toList()));
    }
}
