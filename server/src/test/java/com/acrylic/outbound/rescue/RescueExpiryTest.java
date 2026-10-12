package com.acrylic.outbound.rescue;

import com.acrylic.outbound.common.BusinessException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RescueExpiryTest {
    private final Instant now=Instant.parse("2026-10-12T00:00:00Z");
    @Test void explicitlyUnboundedReadScopeHasNoArtificialWorkExpiry() {
        assertThat(RescueApprovals.expiry(null,now)).isNull();
    }
    @Test void expiryKeepsOnlyDatabaseRepresentableMilliseconds() {
        assertThat(RescueApprovals.expiry(now.plusNanos(1001001),now)).isEqualTo(now.plusMillis(1));
    }
    @Test void submillisecondExpiryCannotBecomeExpiredOnInsert() {
        assertThatThrownBy(()->RescueApprovals.expiry(now.plusNanos(999999),now)).isInstanceOf(BusinessException.class);
    }
    @Test void farFutureOutsideMysqlDateRangeIsRejectedBeforeSigning() {
        assertThatThrownBy(()->RescueApprovals.expiry(Instant.parse("+10000-01-01T00:00:00Z"),now)).isInstanceOf(BusinessException.class);
    }
}
