package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Measured identity-format conflict between the two opt-in layers: the admission path loads the
 * v1 initial-authority field set, while the P5 lease path demands the v2 shape with
 * {@code authorityKind}, {@code closedAuthority} and {@code leaseRoot}. Until a supplier writes the
 * v2 shape the two layers cannot be enabled in the same context, which the field run shows as a
 * startup refusal instead of a silent downgrade.
 */
class RecoveryIdentityFormatConflictTest {
    @TempDir Path parent;

    @Test void theAdmissionPathAcceptsTheV1Identity() throws Exception {
        var control = RecoveryWriterLeaseTest.protocol();

        var registry = new RecoveryOperationRegistry(control.location(), control.checkpoint);

        assertThat(registry.startup().installationId()).isEqualTo(control.installation);
    }

    @Test void theAdmissionPathRefusesTheV2IdentityTheLeasePathDemands() throws Exception {
        var control = RecoveryWriterLeaseTest.protocol();
        Path identityPath = control.location().root().resolve("identity.json");
        ObjectNode identity = (ObjectNode) RecoveryControlHistory.read(identityPath);
        identity.put("formatVersion", 2).put("authorityKind", "INITIAL_NORMAL_INSTALL")
            .put("closedAuthority", parent.resolve("closed").toString())
            .put("leaseRoot", parent.resolve("p5").toString());
        RecoveryControlFixture.write(identityPath, identity);

        assertThatThrownBy(() -> new RecoveryOperationRegistry(control.location(), control.checkpoint))
            .hasMessageContaining("CONTROL_FIELDS");
    }

    @Test void provisioningRefusesAV1Identity() throws Exception {
        var authority = Files.createDirectory(parent.resolve("authority"));
        var business = Files.createDirectory(parent.resolve("business"));
        var installation = UUID.randomUUID();
        var value = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
            .put("formatVersion", 1).put("installationId", installation.toString())
            .put("initialCommandSha256", "a".repeat(64));
        value.putArray("restoreRoots").add(business.toRealPath().toString());
        Files.writeString(authority.resolve("identity.json"), RecoveryControlFormat.canonical(value));
        var location = new RecoveryControlLocation(authority, installation, List.of(business));
        var binding = new RecoveryLeaseRoots.Binding(location, parent.resolve("p5"), parent.resolve("closed"),
            RecoveryControlFormat.hash(value));

        assertThatThrownBy(() -> RecoveryLeaseRoots.provision(binding, new byte[32]))
            .as("the P5 binding validates the v2 field set before it ever looks at the version")
            .hasMessageContaining("CONTROL_FIELDS");
    }
}
