package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class HandoverTemporaryCredentialTest {
    @TempDir Path directory;

    @Test void originalSignaturesAndPermittedDeviceClockSkewRemainUsable() throws Exception {
        var f = new HandoverTemporaryCredentialFixture(directory);
        f.acknowledge(f.signer, f.now.plusSeconds(120), f.now);
        assertThat(f.reader(f.signer).verify(f.row())).isEqualTo(f.manifest);
    }

    @Test void acknowledgementCanUseRotatedTrustedKeyWhileAuthorizationKeepsOriginalKey() throws Exception {
        var f = new HandoverTemporaryCredentialFixture(directory);
        var rotated = HandoverTemporaryCredentialFixture.signer(directory, "second", directory.resolve("first-public.der").toString());
        f.acknowledge(rotated, f.now, f.now);
        assertThat(f.reader(rotated).verify(f.row())).isEqualTo(f.manifest);
    }

    @ParameterizedTest @ValueSource(strings = {"jti", "purpose", "scope", "manifestSha256", "iat", "exp"})
    void validSignatureWithInconsistentAuthorizationBindingIsRejected(String field) throws Exception {
        var f = new HandoverTemporaryCredentialFixture(directory);
        f.auth.put(field, "changed");
        refused(f);
    }

    @ParameterizedTest @ValueSource(strings = {"authorizationId", "bundleId", "manifestSha256", "iat", "exp"})
    void validSignatureWithInconsistentManifestProofIsRejected(String field) throws Exception {
        var f = new HandoverTemporaryCredentialFixture(directory);
        f.proof.put(field, "changed");
        refused(f);
    }

    @Test void missingOrUntrustedAcknowledgementCannotPrepareRecipient() throws Exception {
        var f = new HandoverTemporaryCredentialFixture(directory);
        f.ack = null; refused(f);
        var unknown = HandoverTemporaryCredentialFixture.signer(directory, "unknown", "");
        f.acknowledge(unknown, f.now, f.now); refused(f);
    }

    @Test void acknowledgementOutsideOriginalClockWindowIsRejected() throws Exception {
        var f = new HandoverTemporaryCredentialFixture(directory);
        f.acknowledge(f.signer, f.now.plusSeconds(301), f.now); refused(f);
        f.acknowledge(f.signer, f.issued.minusSeconds(1), f.now); refused(f);
        f.acknowledge(f.signer, f.expires, f.expires); refused(f);
    }

    private void refused(HandoverTemporaryCredentialFixture f) {
        assertThatThrownBy(() -> f.reader(f.signer).verify(f.row())).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("HANDOVER_TARGET_CREDENTIAL_INVALID"));
    }
}
