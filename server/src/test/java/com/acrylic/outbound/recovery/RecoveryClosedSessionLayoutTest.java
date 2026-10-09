package com.acrylic.outbound.recovery;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RecoveryClosedSessionLayoutTest {
    @Test void registrationDoesNotSupplyTheIndependentAuthorityPin() throws Exception {
        var supply = RecoveryClosedTestSupply.of(RecoverySourceFixture.create());
        assertThat(new RecoveryControlReader(supply.location).read(supply.checkpoint)).isNotNull();
        assertThatThrownBy(() -> new RecoveryClosedAuthority(supply.location, supply.checkpoint,
            new RecoveryClosedAuthority.Pins(supply.root, "f".repeat(64), supply.source)))
            .hasMessage("RECOVERY_CLOSED_AUTHORITY_PIN_MISMATCH");
    }

    @Test void unknownRegistrationFilenameRefusesTheEntireControlLoad() throws Exception {
        var supply = RecoveryClosedTestSupply.of(RecoverySourceFixture.create());
        Files.writeString(supply.location.root().resolve("closed-sessions/unrecognized.json"), "{}");
        assertThatThrownBy(supply::authority).hasMessage("CONTROL_CLOSED_SESSION_LAYOUT");
    }

    @Test void directoryCannotBeReplacedWithAnOpaqueFile() throws Exception {
        var supply = RecoveryClosedTestSupply.of(RecoverySourceFixture.create());
        Path directory = supply.location.root().resolve("closed-sessions");
        Files.move(directory, supply.root.resolve("retained-registrations"));
        Files.writeString(directory, "{}");
        assertThatThrownBy(supply::authority).hasMessage("CONTROL_CLOSED_SESSION_LAYOUT");
    }

    @Test void extraRegistrationFieldsNeverBecomePermissions() throws Exception {
        var supply = RecoveryClosedTestSupply.of(RecoverySourceFixture.create());
        Path file = supply.location.root().resolve("closed-sessions/"
            + supply.authority().control().recordSha256() + ".json");
        var value = (com.fasterxml.jackson.databind.node.ObjectNode)RecoveryControlHistory.read(file);
        value.put("ordinaryWritesAllowed", true); RecoveryControlFixture.write(file, value);
        assertThatThrownBy(supply::authority).hasMessage("CONTROL_FIELDS");
    }
}
