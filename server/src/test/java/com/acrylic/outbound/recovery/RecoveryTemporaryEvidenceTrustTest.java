package com.acrylic.outbound.recovery;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoveryTemporaryEvidenceTrustTest {
    @Test void sameOriginalRootAndControlledWindowAreAcceptedWithoutGrantingWrites() throws Exception {
        var f=new RecoveryTemporaryEvidenceTrustFixture();
        f.verify();
        assertThat(f.root().ordinaryWritesAllowed()).isFalse(); assertThat(f.root().localDeletionAllowed()).isFalse();
    }
    @Test void anotherDatabaseInstanceCannotDonateHistoricalPermission() throws Exception {
        var f=new RecoveryTemporaryEvidenceTrustFixture();
        var wrong=new RecoveryDeviceRegistrationCapture.Database(UUID.randomUUID(),f.database.schema(),"1");
        assertThatThrownBy(() -> RecoveryTemporaryEvidenceTrust.verify(f.registration.nativeSource.initial.source(),f.root(),
            f.registration.nativeSource.evidence.request(),f.permission,List.of(f.log()),wrong)).hasMessage("RECOVERY_EVIDENCE_REGISTRATION_SOURCE");
    }
    @Test void anotherOriginalKeyCannotDonateRegistration() throws Exception {
        var f=new RecoveryTemporaryEvidenceTrustFixture(); var other=new RecoveryDeviceRegistrationFixture(); var root=f.root();
        var wrong=new RecoveryDeviceRegistration.Proof(root.expected(),root.originalSha256(),root.observedAt(),other.read().trustedKey());
        assertThatThrownBy(() -> RecoveryTemporaryEvidenceTrust.verify(f.registration.nativeSource.initial.source(),wrong,
            f.registration.nativeSource.evidence.request(),f.permission,List.of(f.log()),f.database)).hasMessage("RECOVERY_EVIDENCE_REGISTRATION_SOURCE");
    }
    @Test void registrationAfterOriginalWindowCannotBackdateTrust() throws Exception {
        var f=new RecoveryTemporaryEvidenceTrustFixture(); var root=f.root();
        var late=new RecoveryDeviceRegistration.Proof(root.expected(),root.originalSha256(),Instant.parse("2026-09-20T00:00:00.001Z"),root.trustedKey());
        assertThatThrownBy(() -> RecoveryTemporaryEvidenceTrust.verify(f.registration.nativeSource.initial.source(),late,
            f.registration.nativeSource.evidence.request(),f.permission,List.of(f.log()),f.database)).hasMessage("RECOVERY_EVIDENCE_PERMISSION_SOURCE");
    }
    @Test void missingControlledWindowCannotBeReplacedByAnObservation() throws Exception {
        var f=new RecoveryTemporaryEvidenceTrustFixture();
        assertThatThrownBy(() -> RecoveryTemporaryEvidenceTrust.verify(f.registration.nativeSource.initial.source(),f.root(),
            f.registration.nativeSource.evidence.request(),f.permission,List.of(),f.database)).hasMessage("RECOVERY_EVIDENCE_PERMISSION_WINDOWS");
    }
    @Test void differentWindowEndpointCannotProveOriginalSaves() throws Exception {
        var f=new RecoveryTemporaryEvidenceTrustFixture(); f.history.logged.end.put("observedAt","2026-09-21T00:00:00.001Z");
        assertThatThrownBy(f::verify).hasMessage("RECOVERY_EVIDENCE_PERMISSION_SOURCE");
    }
    @ParameterizedTest @ValueSource(strings={"userAuthVersion","deviceVersion"})
    void changedEffectiveVersionCannotProvideOriginalPermission(String field) throws Exception {
        var f=new RecoveryTemporaryEvidenceTrustFixture();
        for(var boundary:List.of(f.history.logged.begin,f.history.logged.end))
            ((com.fasterxml.jackson.databind.node.ObjectNode)boundary.path("effective")).put(field,"999");
        assertThatThrownBy(f::verify).hasMessage("RECOVERY_EVIDENCE_PERMISSION_STATE");
    }
}
