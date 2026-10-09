package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoverySourceFormalRenewalsTest {
    @Test void verifiesEveryOriginalWindowAgainstTheFirstUnchangedPreparation() throws Exception {
        var f = new RecoveryFormalRenewalFixture();

        var proof = f.reader().verify(f.renewed.source.read(), f.catalog());

        assertThat(proof.authorizationIds()).containsExactly(UUID.fromString(f.first.state.authority.claims.path("jti").asText()),
            UUID.fromString(f.renewed.claims.path("jti").asText()));
        assertThat(proof.preparation().manifestSha256()).isEqualTo(f.first.receipt.path("manifestSha256").asText());
        assertThat(f.renewed.source.read().ordinaryWritesAllowed()).isFalse(); assertThat(f.renewed.source.read().localDeletionAllowed()).isFalse();
    }

    @Test void refusesMissingInitialCredential() throws Exception {
        var f = new RecoveryFormalRenewalFixture();

        assertThatThrownBy(() -> f.reader().verify(f.renewed.source.read(), Map.of(UUID.fromString(f.renewed.claims.path("jti").asText()),
            f.renewed.source.read()))).hasMessage("RECOVERY_FORMAL_RENEWAL_CATALOG");
    }

    @Test void refusesACatalogEndingAfterTheCurrentCredential() throws Exception {
        var f = new RecoveryFormalRenewalFixture();

        assertThatThrownBy(() -> f.reader().verify(f.first.read(), f.catalog())).hasMessage("RECOVERY_FORMAL_RENEWAL_CURRENT");
    }

    @ParameterizedTest @ValueSource(strings = {"0", "2"})
    void refusesSequenceGapsOrForks(String sequence) throws Exception {
        var f = new RecoveryFormalRenewalFixture(); f.renewed.claims.put("authorizationSequence", sequence); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.renewed.source.read(), f.catalog())).hasMessage("RECOVERY_FORMAL_RENEWAL_CHAIN");
    }

    @Test void refusesWrongSignedPredecessor() throws Exception {
        var f = new RecoveryFormalRenewalFixture(); f.renewed.claims.put("renewedFromAuthorizationId", UUID.randomUUID().toString()); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.renewed.source.read(), f.catalog())).hasMessage("RECOVERY_FORMAL_RENEWAL_CHAIN");
    }

    @ParameterizedTest @ValueSource(strings = {"planSha256", "inventorySha256", "policyVersion", "userAuthVersion", "deviceVersion", "allocationVersion"})
    void refusesAChangedSignedPreparationBaseline(String field) throws Exception {
        var f = new RecoveryFormalRenewalFixture(); f.renewed.claims.put(field, "2"); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.renewed.source.read(), f.catalog())).hasMessage("RECOVERY_FORMAL_RENEWAL_BASELINE");
    }

    @Test void refusesRenewingTheOriginalPreparationExpiry() throws Exception {
        var f = new RecoveryFormalRenewalFixture(); var receipt = (ObjectNode)f.origin.path("preparationReceipt");
        receipt.put("manifest", receipt.path("manifest").asText().replace("2026-09-21T00:00:00Z", "2026-09-23T00:00:00Z")); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.renewed.source.read(), f.catalog())).hasMessage("RECOVERY_FORMAL_RENEWAL_PREPARATION");
    }

    @Test void refusesChangedOriginalParts() throws Exception {
        var f = new RecoveryFormalRenewalFixture(); ((ObjectNode)f.origin.path("parts")).put("inventory", "AA=="); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.renewed.source.read(), f.catalog())).hasMessage("RECOVERY_FORMAL_RENEWAL_PREPARATION");
    }

    @Test void refusesANewActivationInPlaceOfTheOriginal() throws Exception {
        var f = new RecoveryFormalRenewalFixture(); f.origin.putObject("activation").put("newWork", true); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.renewed.source.read(), f.catalog())).hasMessage("RECOVERY_FORMAL_RENEWAL_PREPARATION");
    }

    @Test void refusesWrongNativeRenewalDuration() throws Exception {
        var f = new RecoveryFormalRenewalFixture(); f.renewed.claims.put("exp", Instant.parse("2026-09-23T00:00:01Z").getEpochSecond()); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.renewed.source.read(), f.catalog())).hasMessage("RECOVERY_FORMAL_RENEWAL_WINDOW");
    }

    @Test void refusesRenewalIssueBeforeTheOriginal() throws Exception {
        var f = new RecoveryFormalRenewalFixture(); f.renewed.claims.put("iat", Instant.parse("2026-09-19T00:00:00Z").getEpochSecond())
            .put("exp", Instant.parse("2026-09-21T00:00:00Z").getEpochSecond()); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.renewed.source.read(), f.catalog())).hasMessage("RECOVERY_FORMAL_RENEWAL_WINDOW");
    }

    @Test void refusesAnOldSaveOutsideItsOwnWindowEvenInsideTheRenewedWindow() throws Exception {
        var f = new RecoveryFormalRenewalFixture(); ((ObjectNode)f.payload.at("/captures/0")).put("confirmedAt", "2026-09-21T00:05:00Z");
        ((ObjectNode)f.payload.at("/scans/0")).put("savedAt", "2026-09-21T00:06:00Z"); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.renewed.source.read(), f.catalog())).hasMessage("RECOVERY_SOURCE_ORIGIN_WINDOW");
    }

    @Test void refusesAnUnknownOriginalCaptureCredential() throws Exception {
        var f = new RecoveryFormalRenewalFixture(); ((ObjectNode)f.payload.at("/captures/0")).put("authorizationId", UUID.randomUUID().toString()); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.renewed.source.read(), f.catalog())).hasMessage("RECOVERY_SOURCE_CREDENTIAL_MISSING");
    }

    @Test void refusesAnUnpinnedOriginalCredentialEvenWithAValidPublicSignature() throws Exception {
        var f = new RecoveryFormalRenewalFixture(); var pinned = f.first.read(); var objects = new HashMap<String, byte[]>();
        for (String kind : RecoverySourceManifest.OBJECTS) objects.put(kind, pinned.original(kind));
        var unpinned = new RecoveryPinnedSource(pinned.identity(), null, null, objects); var catalog = new HashMap<>(f.catalog());
        catalog.put(UUID.fromString(f.first.state.authority.claims.path("jti").asText()), unpinned);

        assertThatThrownBy(() -> f.reader().verify(f.renewed.source.read(), catalog)).hasMessage("RECOVERY_FORMAL_RENEWAL_PIN");
    }

    @Test void refusesAnUnrelatedPinnedSourceInTheRenewalCatalog() throws Exception {
        var f = new RecoveryFormalRenewalFixture(); var foreign = new RecoveryAuthorityFixture("TEMP"); var catalog = new HashMap<>(f.catalog());
        catalog.put(UUID.fromString(foreign.claims.path("jti").asText()), foreign.source.read());

        assertThatThrownBy(() -> f.reader().verify(f.renewed.source.read(), catalog)).hasMessage("RECOVERY_FORMAL_RENEWAL_SOURCE");
    }

    @Test void acceptsACompleteThirdCredentialWithOnlyEarlierValidSaves() throws Exception {
        var f = new RecoveryFormalRenewalFixture(); var third = new RecoveryAuthorityFixture(RecoverySourceFixture.credentialCopy(f.renewed.source));
        third.claims.removeAll(); third.claims.setAll(f.renewed.claims.deepCopy());
        third.claims.put("jti", UUID.randomUUID().toString()).put("authorizationSequence", "2")
            .put("renewedFromAuthorizationId", f.renewed.claims.path("jti").asText())
            .put("iat", Instant.parse("2026-09-22T00:00:00Z").getEpochSecond()).put("exp", Instant.parse("2026-09-24T00:00:00Z").getEpochSecond());
        var origin = f.origin.deepCopy(); var header = com.acrylic.outbound.submissions.SubmissionJson.object()
            .put("alg", "RS256").put("typ", "JWT").put("kid", third.key.kid);
        origin.put("offlineToken", third.key.token(header, third.claims));
        third.key.object(third.source, "origin", RecoveryReceiptFixture.bytes(origin));
        third.key.object(third.source, "payload", RecoveryReceiptFixture.bytes(f.payload)); third.source.publish();
        var catalog = new HashMap<>(f.catalog()); catalog.put(UUID.fromString(third.claims.path("jti").asText()), third.source.read());
        var keys = new RecoveryReceiptKeys(List.of(f.first.state.authority.key.pair.getPublic().getEncoded(),
            f.renewed.key.pair.getPublic().getEncoded(), third.key.pair.getPublic().getEncoded()));

        assertThat(new RecoverySourceFormalRenewals(keys).verify(third.source.read(), catalog).authorizationIds()).hasSize(3);
    }
}
