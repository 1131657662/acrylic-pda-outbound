package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.devices.DeviceCrypto;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SourceEvidenceIntakeDeviceKeysTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SourceEvidenceIntakeDeviceKeys keys = new SourceEvidenceIntakeDeviceKeys(jdbc);
    private final UUID warehouse = UUID.randomUUID(), device = UUID.randomUUID();
    private HashMap<String, Object> row;

    @BeforeEach void pairedKey() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        String encoded = java.util.Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded());
        row = new HashMap<>();
        row.put("warehouse_id", Ids.bytes(warehouse));
        row.put("pairing_key", encoded);
        row.put("device_key", encoded);
        row.put("pairing_hash", DeviceCrypto.sha(encoded));
        row.put("device_hash", DeviceCrypto.sha(encoded));
        row.put("pairing_id", Ids.bytes(UUID.randomUUID()));
        row.put("pairing_status", "COMPLETED");
        row.put("pairing_approver", Ids.bytes(UUID.randomUUID()));
        row.put("pairing_approval", "{}");
        row.put("pairing_approval_hash", "c".repeat(64));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));
    }

    @Test void completedPairingAndCurrentRegistrationProvideSameP256Key() {
        assertThat(keys.trusted(warehouse, device).getEncoded())
            .containsExactly(DeviceCrypto.publicKey((String) row.get("pairing_key")).getEncoded());
    }

    @Test void directlyRegisteredCurrentKeyIsAlsoServerKnown() {
        row.remove("pairing_id");
        assertThat(keys.trusted(warehouse, device).getEncoded())
            .containsExactly(DeviceCrypto.publicKey((String) row.get("device_key")).getEncoded());
    }

    @Test void unknownDeviceHasNoTrustedKey() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        assertUnverifiable();
    }

    @Test void foreignWarehouseCannotBorrowDeviceKey() {
        row.put("warehouse_id", Ids.bytes(UUID.randomUUID()));
        assertUnverifiable();
    }

    @Test void changedOrCorruptRegisteredKeyCannotBeTrusted() {
        row.put("device_hash", "f".repeat(64));
        assertUnverifiable();
        row.put("device_hash", row.get("pairing_hash"));
        row.put("device_key", "changed");
        assertUnverifiable();
    }

    @Test void completedPairingCannotClaimAnotherCurrentDeviceKey() {
        row.put("pairing_key", "changed");
        assertUnverifiable();
        row.put("pairing_key", row.get("device_key"));
        row.put("pairing_status", "WAITING");
        assertUnverifiable();
    }

    @Test void malformedPairingKeyFailsClosed() {
        row.put("pairing_key", "not-an-ec-key");
        row.put("device_key", "not-an-ec-key");
        row.put("pairing_hash", DeviceCrypto.sha("not-an-ec-key"));
        row.put("device_hash", DeviceCrypto.sha("not-an-ec-key"));
        assertUnverifiable();
    }

    private void assertUnverifiable() {
        assertThatThrownBy(() -> keys.trusted(warehouse, device)).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("E2A_STOP_KEY_UNVERIFIABLE"));
    }
}
