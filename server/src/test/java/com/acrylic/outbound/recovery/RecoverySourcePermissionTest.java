package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoverySourcePermissionTest {
    @ParameterizedTest @ValueSource(strings = {"FORMAL", "TEMP"})
    void provesHistoricalPermissionAtOriginalConfirmationAndSave(String kind) throws Exception {
        var f = new RecoverySourceStateFixture(kind); var source = f.read();

        var proof = f.permissionReader().verify(source);

        assertThat(proof.windows()).hasSize(1);
        assertThat(source.ordinaryWritesAllowed()).isFalse(); assertThat(source.localDeletionAllowed()).isFalse();
    }

    @Test void doesNotEraseLegalSavesBecauseTheIdentityWasRevokedLater() throws Exception {
        var f = new RecoverySourceStateFixture("TEMP"); f.firstWindow().put("through", "2026-09-20T00:32:00Z");
        var revoked = f.window().put("from", "2026-09-20T00:32:00Z").put("userActive", false).put("deviceStatus", "DISABLED");
        ((ArrayNode)f.permission.path("windows")).add(revoked); f.publish();

        assertThat(f.permissionReader().verify(f.read()).windows()).hasSize(2);
    }

    @ParameterizedTest @ValueSource(strings = {"userId", "deviceId", "warehouseId"})
    void refusesForeignHistoricalIdentity(String field) throws Exception {
        var f = new RecoverySourceStateFixture("FORMAL"); f.firstWindow().put(field, UUID.randomUUID().toString()); f.publish();

        assertThatThrownBy(() -> f.permissionReader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PERMISSION_IDENTITY");
    }

    @ParameterizedTest @ValueSource(strings = {"userAuthVersion", "deviceVersion"})
    void refusesVersionsDifferentFromTheSignedOriginalCredential(String field) throws Exception {
        var f = new RecoverySourceStateFixture("FORMAL"); f.firstWindow().put(field, "99"); f.publish();

        assertThatThrownBy(() -> f.permissionReader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PERMISSION_VERSION");
    }

    @ParameterizedTest @ValueSource(strings = {"userActive", "deviceStatus"})
    void refusesRevokedIdentityAtTheOriginalSave(String field) throws Exception {
        var f = new RecoverySourceStateFixture("TEMP");
        if (field.equals("userActive")) f.firstWindow().put(field, false); else f.firstWindow().put(field, "DISABLED"); f.publish();

        assertThatThrownBy(() -> f.permissionReader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PERMISSION_REVOKED");
    }

    @ParameterizedTest @ValueSource(strings = {"pda:login", "pda:scan", "pda:submit", "temporary_order:create"})
    void refusesMissingTemporaryPermission(String missing) throws Exception {
        var f = new RecoverySourceStateFixture("TEMP"); var permissions = f.firstWindow().putArray("permissions");
        for (String scope : java.util.List.of("pda:login", "pda:scan", "pda:submit", "temporary_order:create"))
            if (!scope.equals(missing)) permissions.add(scope);
        f.publish();

        assertThatThrownBy(() -> f.permissionReader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PERMISSION_SCOPE");
    }

    @Test void refusesTemporaryWorkOnADeviceWithoutTemporaryAdmission() throws Exception {
        var f = new RecoverySourceStateFixture("TEMP"); f.firstWindow().put("deviceTemporaryOrderEnabled", false); f.publish();

        assertThatThrownBy(() -> f.permissionReader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PERMISSION_SCOPE");
    }

    @Test void refusesAUserOutsideTheWarehouse() throws Exception {
        var f = new RecoverySourceStateFixture("FORMAL"); f.firstWindow().putArray("warehouseIds").add(UUID.randomUUID().toString()); f.publish();

        assertThatThrownBy(() -> f.permissionReader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PERMISSION_SCOPE");
    }

    @ParameterizedTest @ValueSource(strings = {"from", "through"})
    void refusesAnOriginalEventOutsideTheArchivedWindow(String field) throws Exception {
        var f = new RecoverySourceStateFixture("FORMAL");
        f.firstWindow().put(field, field.equals("from") ? "2026-09-20T00:30:01Z" : "2026-09-20T00:31:00Z"); f.publish();

        assertThatThrownBy(() -> f.permissionReader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PERMISSION_WINDOW");
    }

    @Test void refusesSaveAtTheRevocationBoundary() throws Exception {
        var f = new RecoverySourceStateFixture("FORMAL"); f.firstWindow().put("through", "2026-09-20T00:31:00Z");
        var revoked = f.window().put("from", "2026-09-20T00:31:00Z").put("deviceStatus", "DISABLED");
        ((ArrayNode)f.permission.path("windows")).add(revoked); f.publish();

        assertThatThrownBy(() -> f.permissionReader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PERMISSION_REVOKED");
    }

    @Test void refusesOverlappingWindows() throws Exception {
        var f = new RecoverySourceStateFixture("FORMAL"); ((ArrayNode)f.permission.path("windows")).add(f.window()); f.publish();

        assertThatThrownBy(() -> f.permissionReader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PERMISSION_WINDOW_ORDER");
    }

    @Test void refusesUnknownGapsEvenIfBothEventsAreInKnownWindows() throws Exception {
        var f = new RecoverySourceStateFixture("FORMAL"); f.firstWindow().put("through", "2026-09-20T00:30:15Z");
        ((ArrayNode)f.permission.path("windows")).add(f.window().put("from", "2026-09-20T00:30:45Z")); f.publish();

        assertThatThrownBy(() -> f.permissionReader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PERMISSION_WINDOW_GAP");
    }

    @Test void acceptsAdjacentWindowsWithHalfOpenBoundaries() throws Exception {
        var f = new RecoverySourceStateFixture("FORMAL"); f.firstWindow().put("through", "2026-09-20T00:31:00Z");
        ((ArrayNode)f.permission.path("windows")).add(f.window().put("from", "2026-09-20T00:31:00Z")); f.publish();

        assertThat(f.permissionReader().verify(f.read()).windows()).hasSize(2);
    }

    @ParameterizedTest @ValueSource(strings = {"payloadSha256", "credentialSha256"})
    void refusesDifferentPinnedOriginalDigest(String field) throws Exception {
        var f = new RecoverySourceStateFixture("FORMAL"); f.permission.put(field, "a".repeat(64)); f.object("permission", f.permission);

        assertThatThrownBy(() -> f.permissionReader().verify(f.read())).hasMessage(field.equals("payloadSha256")
            ? "RECOVERY_SOURCE_PERMISSION_PAYLOAD" : "RECOVERY_SOURCE_PERMISSION_CREDENTIAL");
    }

    @Test void refusesDifferentPinnedSource() throws Exception {
        var f = new RecoverySourceStateFixture("TEMP"); ((ObjectNode)f.permission.path("source")).put("operatorId", UUID.randomUUID().toString());
        f.object("permission", f.permission);

        assertThatThrownBy(() -> f.permissionReader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PERMISSION_SOURCE");
    }

    @Test void refusesMissingOrInventedPermissionStateFields() throws Exception {
        var f = new RecoverySourceStateFixture("TEMP"); f.firstWindow().remove("userActive"); f.publish();

        assertThatThrownBy(() -> f.permissionReader().verify(f.read())).hasMessage("CONTROL_FIELDS");
    }

    @ParameterizedTest @ValueSource(strings = {"continuationId", "handoverSessionId", "recoveryPackageId", "handoverId"})
    void refusesUnsupportedInitialCredentialChain(String field) throws Exception {
        var f = new RecoverySourceStateFixture("FORMAL"); f.payload.put(field, UUID.randomUUID().toString()); f.publish();

        assertThatThrownBy(() -> f.permissionReader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PERMISSION_CHAIN_UNSUPPORTED");
    }

    @Test void refusesConfirmationAtCredentialExpiryEvenWithoutASuccessfulSave() throws Exception {
        var f = new RecoverySourceStateFixture("FORMAL");
        ((ObjectNode)f.payload.path("captures").get(0)).put("state", "BUSINESS_REJECTED").put("confirmedAt", "2026-09-21T00:00:00Z");
        ((ArrayNode)f.payload.path("scans")).removeAll(); f.firstWindow().put("through", "2026-09-22T00:00:00Z"); f.publish();

        assertThatThrownBy(() -> f.permissionReader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PERMISSION_CREDENTIAL_WINDOW");
    }

    @Test void refusesMissingOriginalSignedVersion() throws Exception {
        var f = new RecoverySourceStateFixture("TEMP"); ((ObjectNode)f.authority.claims.path("identity")).remove("deviceVersion");
        f.authority.publish(); f.publish();

        assertThatThrownBy(() -> f.permissionReader().verify(f.read())).hasMessage("CONTROL_TEXT");
    }

    @Test void refusesHigherRoundWithoutItsActivationProof() throws Exception {
        var f = new RecoverySourceStateFixture("FORMAL"); var later = RecoverySourceFixture.create();
        f.authority.key.object(later, "origin", f.read().original("origin")); later.publish();

        assertThatThrownBy(() -> f.permissionReader().verify(later.read())).hasMessage("RECOVERY_SOURCE_ORIGIN_ROUND_UNSUPPORTED");
    }

    @Test void refusesDispositionChainWithOnlyAnInitialCredential() throws Exception {
        var f = new RecoverySourceStateFixture("FORMAL"); f.payload.putArray("dispositions").addObject().put("action", "RETAIN"); f.publish();

        assertThatThrownBy(() -> f.permissionReader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PERMISSION_CHAIN_UNSUPPORTED");
    }
}
