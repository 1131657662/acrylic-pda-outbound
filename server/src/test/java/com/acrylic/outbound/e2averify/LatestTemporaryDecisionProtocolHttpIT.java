package com.acrylic.outbound.e2averify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** D04 independent candidate consumer on the full nonempty migration chain; the legacy test remains separate. */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class LatestTemporaryDecisionProtocolHttpIT {
    @Test void completeCandidatesAndOriginalDecisionsMatchIndependentSqlReconstruction() throws Exception {
        E2aTemporaryDecisionProtocolIT.run("latest");
    }
}
