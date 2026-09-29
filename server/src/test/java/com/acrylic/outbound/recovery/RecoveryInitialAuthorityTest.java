package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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
 * Design row 6: the initial-authority shape is version aware and stays exact in both versions, so
 * the admission path (which reads the legacy v1 identity) and the P5 layer (which demands the v2
 * {@code INITIAL_NORMAL_INSTALL} shape) accept the same file, while an unknown field, a missing
 * field, a foreign kind or an unimplemented version is still refused.
 */
class RecoveryInitialAuthorityTest {
    @TempDir Path parent;

    @Test void theAdmissionPathStillAcceptsTheLegacyV1Identity() throws Exception {
        var control = RecoveryWriterLeaseTest.protocol();

        var registry = new RecoveryOperationRegistry(control.location(), control.checkpoint);

        assertThat(registry.startup().installationId()).isEqualTo(control.installation);
    }

    @Test void theAdmissionPathAcceptsThePromotedV2InitialNormalIdentity() throws Exception {
        // The writer protocol pins the identity hash, so the promotion happens first and the protocol
        // is derived from the v2 identity; promoting an installation after that would be a mismatch.
        var promoted = new RecoveryInitialAuthorityProvision.Provisioned[1];
        var control = RecoveryWriterLeaseTest.protocol(fixture -> {
            promoted[0] = RecoveryInitialAuthorityProvision.provision(fixture.root, parent.resolve("p5"),
                parent.resolve("closed"));
            return RecoveryControlHistory.read(fixture.root.resolve("identity.json"));
        });

        var registry = new RecoveryOperationRegistry(control.location(), control.checkpoint);

        assertThat(registry.startup().installationId()).isEqualTo(control.installation);
        assertThat(RecoveryControlHistory.read(control.location().root().resolve("identity.json"))
            .path("authorityKind").asText()).isEqualTo("INITIAL_NORMAL_INSTALL");
        assertThat(promoted[0].markerSha256()).hasSize(64);
        assertThat(Files.exists(parent.resolve("p5").resolve("lease-root.json"))).isTrue();
    }

    @Test void aV2IdentityWithAForeignKindIsRefused() throws Exception {
        var control = RecoveryWriterLeaseTest.protocol(fixture -> {
            RecoveryInitialAuthorityProvision.provision(fixture.root, parent.resolve("p5"), parent.resolve("closed"));
            var promoted = (ObjectNode) RecoveryControlHistory.read(fixture.root.resolve("identity.json"));
            promoted.put("authorityKind", "INITIAL_CLOSED_INSTALL");
            RecoveryControlFixture.write(fixture.root.resolve("identity.json"), promoted);
            return promoted;
        });
        Path identityPath = control.location().root().resolve("identity.json");
        assertThatThrownBy(() -> new RecoveryOperationRegistry(control.location(), control.checkpoint))
            .hasMessageContaining("CONTROL_AUTHORITY_PURPOSE_FORBIDDEN");
        assertThat(identityPath).isNotNull();
    }

    @Test void aV2IdentityWithoutTheP5PathsIsRefused() throws Exception {
        var control = RecoveryWriterLeaseTest.protocol();
        Path identityPath = control.location().root().resolve("identity.json");
        ObjectNode identity = (ObjectNode) RecoveryControlHistory.read(identityPath);
        identity.put("formatVersion", 2).put("authorityKind", "INITIAL_NORMAL_INSTALL");
        RecoveryControlFixture.write(identityPath, identity);

        assertThatThrownBy(() -> new RecoveryOperationRegistry(control.location(), control.checkpoint))
            .hasMessageContaining("CONTROL_FIELDS");
    }

    @Test void anUnknownFieldOrAnUnimplementedVersionIsRefused() throws Exception {
        var control = RecoveryWriterLeaseTest.protocol();
        Path identityPath = control.location().root().resolve("identity.json");
        ObjectNode original = (ObjectNode) RecoveryControlHistory.read(identityPath);

        ObjectNode unknown = original.deepCopy().put("closedAuthority", parent.resolve("closed").toString());
        RecoveryControlFixture.write(identityPath, unknown);
        assertThatThrownBy(() -> new RecoveryOperationRegistry(control.location(), control.checkpoint))
            .hasMessageContaining("CONTROL_FIELDS");

        ObjectNode future = original.deepCopy().put("formatVersion", 3);
        RecoveryControlFixture.write(identityPath, future);
        assertThatThrownBy(() -> new RecoveryOperationRegistry(control.location(), control.checkpoint))
            .hasMessageContaining("CONTROL_FORMAT_UNSUPPORTED");
    }

    @Test void theP5ProvisioningRefusesALegacyIdentityAndAcceptsThePromotedOne() throws Exception {
        var authority = Files.createDirectory(parent.resolve("authority")).toRealPath();
        var business = Files.createDirectory(parent.resolve("business")).toRealPath();
        var installation = UUID.randomUUID();
        var legacy = JsonNodeFactory.instance.objectNode().put("formatVersion", 1)
            .put("installationId", installation.toString()).put("initialCommandSha256", "a".repeat(64));
        legacy.putArray("restoreRoots").add(business.toRealPath().toString());
        Files.writeString(authority.resolve("identity.json"), RecoveryControlFormat.canonical(legacy));
        Files.writeString(authority.resolve("coordinator.lock"), "0");
        var location = new RecoveryControlLocation(authority, installation, List.of(business));
        var binding = new RecoveryLeaseRoots.Binding(location, parent.resolve("p5"), parent.resolve("closed"),
            RecoveryControlFormat.hash(legacy));

        assertThatThrownBy(() -> RecoveryLeaseRoots.provision(binding, new byte[32]))
            .as("the P5 binding needs the explicit v2 shape, so a legacy identity is refused")
            .hasMessageContaining("CONTROL_FIELDS");

        var promoted = RecoveryInitialAuthorityProvision.provision(authority, parent.resolve("p5"),
            parent.resolve("closed"));
        assertThat(promoted.identitySha256()).hasSize(64);
    }
}
