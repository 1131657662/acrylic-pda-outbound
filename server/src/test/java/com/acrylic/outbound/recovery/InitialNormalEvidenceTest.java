package com.acrylic.outbound.recovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class InitialNormalEvidenceTest {
    @Test void retainsOriginalRequestAndProtectsItsBytes() {
        byte[] original = RecoveryControlJson.encode(InitialNormalFixture.request());
        var evidence = InitialNormalEvidence.parse(original);
        original[0] = 0;
        byte[] returned = evidence.originalBytes(); returned[0] = 0;
        assertThat(evidence.originalBytes()).isEqualTo(RecoveryControlJson.encode(InitialNormalFixture.request()));
        assertThat(evidence.installation()).isEqualTo(InitialNormalFixture.INSTALLATION);
        assertThat(evidence.numericEpoch()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"installationId", "epochId", "numericEpoch", "mysqlServerUuid",
        "commandId", "databaseSchema", "sourceEvidenceSha256", "operatorId", "requestedAt"})
    void missingOriginalFactIsRejected(String field) {
        var input = InitialNormalFixture.request(); input.remove(field);
        assertThatThrownBy(() -> InitialNormalEvidence.parse(RecoveryControlJson.encode(input)))
            .isInstanceOf(RecoveryControlException.class);
    }

    @Test void closedPhaseAndNoncanonicalRequestAreRejected() {
        var input = InitialNormalFixture.request().put("kind", "RELEASE_READY");
        assertThatThrownBy(() -> InitialNormalEvidence.parse(RecoveryControlJson.encode(input)))
            .hasMessage("INITIAL_NORMAL_KIND");
        assertThatThrownBy(() -> InitialNormalEvidence.parse(InitialNormalFixture.request().toString().getBytes()))
            .isInstanceOf(RecoveryControlException.class);
    }

    @Test void invalidMappingAndUnprovenSourceAreRejected() {
        var input = InitialNormalFixture.request().put("numericEpoch", "0");
        assertThatThrownBy(() -> InitialNormalEvidence.parse(RecoveryControlJson.encode(input)))
            .hasMessage("INITIAL_NORMAL_EPOCH");
        input.put("numericEpoch", "1").put("sourceEvidenceSha256", "0".repeat(64));
        assertThatThrownBy(() -> InitialNormalEvidence.parse(RecoveryControlJson.encode(input)))
            .hasMessage("INITIAL_NORMAL_SOURCE_REQUIRED");
    }
}
