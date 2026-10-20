package com.acrylic.outbound.recovery;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class RecoveryLeaseDeferredVisibilityTest {
    @TempDir Path parent;

    @Test void requestedShutdownKeepsOwnerAliveUntilTheLastOperationFinishes() throws Exception {
        var supply = FenceTestSupply.create(parent);
        var authority = supply.authority();
        authority.validateStartup();
        var operation = authority.registerOperation();
        var record = supply.roots().root().resolve("leases/" + operation + ".json");
        byte[] before = Files.readAllBytes(record);

        authority.retire();

        assertThat(RecoveryLeaseVisibility.counts(supply.roots()).orphansObserved()).isZero();
        assertThat(Files.readAllBytes(record)).isEqualTo(before);
        assertThatThrownBy(authority::registerOperation).hasMessage("CONTROL_RETIREMENT_IN_PROGRESS");
        assertThat(authority.retireOperation(operation)).isTrue();
        assertThat(record).doesNotExist();
        assertThat(RecoveryLeaseVisibility.counts(supply.roots()).outstanding()).isFalse();
        authority.retire();
    }
}
