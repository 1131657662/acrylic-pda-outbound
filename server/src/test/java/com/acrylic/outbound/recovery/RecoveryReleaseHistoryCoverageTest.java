package com.acrylic.outbound.recovery;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class RecoveryReleaseHistoryCoverageTest {
    @Test void acceptsExactControlledWindowWithoutBroadeningIt() {
        var proof=new RecoveryControlledPermissionFixture().verify();
        RecoveryReleaseHistoryCoverage.requireCoverage(control(proof.from(),proof.through()),proof);
    }

    @ParameterizedTest @CsvSource({"-1,0","0,1","-1,1","-864000,-863999"})
    void refusesUncoveredLossBeforeOrAfterPinnedHistory(long from,long through) {
        var proof=new RecoveryControlledPermissionFixture().verify();
        assertThatThrownBy(()->RecoveryReleaseHistoryCoverage.requireCoverage(
            control(proof.from().plusSeconds(from),proof.through().plusSeconds(through)),proof))
            .hasMessage("RECOVERY_RELEASE_HISTORY_GAP");
    }

    @Test void ordinaryLoggedWindowCannotSubstituteForControlledHistory() {
        var proof=RecoveryPermissionContinuity.verify(new RecoveryPermissionLogFixture().request());
        assertThatThrownBy(()->RecoveryReleaseHistoryCoverage.requireCoverage(control(proof.from(),proof.through()),proof))
            .hasMessage("RECOVERY_RELEASE_HISTORY_GAP");
    }

    private static RecoveryControlSnapshot control(Instant from,Instant through) {
        return new RecoveryControlSnapshot(UUID.randomUUID(),"2",UUID.randomUUID(),UUID.randomUUID(),"RECOVERING",
            "snapshot","a".repeat(64),"b".repeat(64),"c".repeat(64),List.of(UUID.randomUUID().toString()),"d".repeat(64),from,through);
    }
}
