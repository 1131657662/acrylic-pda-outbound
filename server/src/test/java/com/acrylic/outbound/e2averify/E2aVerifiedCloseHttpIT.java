package com.acrylic.outbound.e2averify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aVerifiedCloseHttpIT {
    @Test void trustedAndroidStopClosesRejectedSourceWithQuarantine() throws Exception {
        E2aVerifiedDispositionHttpIT.run(true);
    }
}
