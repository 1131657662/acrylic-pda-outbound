package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

class RecoveryOperationsAuthorityTest {
    @Test void readsOnlyExplicitPinnedIndependentIdentity() throws Exception {
        var f=RecoveryOperationsFixture.create(); var authority=f.authority();
        assertThat(authority.identity()).isEqualTo(f.identity); authority.requireUnchanged();
        assertThatThrownBy(()->new RecoveryOperationsAuthority(f.closed,f.identityFile,"0".repeat(64)))
            .hasMessage("RECOVERY_OPERATIONS_PIN_REQUIRED");
        assertThatThrownBy(()->new RecoveryOperationsAuthority(f.closed,f.identityFile,"a".repeat(64)))
            .hasMessage("RECOVERY_OPERATIONS_PIN_MISMATCH");
        f.identity.put("operatorId",UUID.randomUUID().toString()); f.publish();
        assertThatThrownBy(authority::requireUnchanged).hasMessage("RECOVERY_OPERATIONS_PIN_MISMATCH");
    }
    @Test void rejectsControlRestoreAndCheckpointLocations() throws Exception {
        var f=RecoveryOperationsFixture.create(); var authority=f.authority();
        var locations=new ArrayList<>(f.closed.location().restoreRoots());
        locations.add(f.closed.location().root()); locations.add(f.closed.authorityRoot());
        for(Path root:locations) {
            Path path=root.resolve("operations-identity.json"); Files.copy(f.identityFile,path);
            assertThatThrownBy(()->new RecoveryOperationsAuthority(f.closed,path,hash(f.identity)))
                .hasMessage(root.equals(f.closed.location().restoreRoots().getFirst())
                    ? "RECOVERY_OPERATIONS_INSIDE_RESTORE_SET" : "RECOVERY_OPERATIONS_AUTHORITY_OVERLAP");
            assertThatThrownBy(()->authority.material(path)).isInstanceOf(RecoveryControlException.class);
        }
        assertThatThrownBy(()->authority.material(f.closed.checkpoint())).hasMessage("RECOVERY_OPERATIONS_AUTHORITY_OVERLAP");
        assertThatThrownBy(()->authority.material(f.identityFile)).hasMessage("RECOVERY_OPERATIONS_MATERIAL_IS_AUTHORITY");
        assertThatThrownBy(()->authority.material(Path.of("relative.json"))).hasMessage("RECOVERY_OPERATIONS_ABSOLUTE_PATH_REQUIRED");
    }
    @Test void rejectsMissingOversizeAliasesAndNoncanonicalIdentity() throws Exception {
        var f=RecoveryOperationsFixture.create(); var authority=f.authority();
        assertThatThrownBy(()->authority.material(f.directory.resolve("missing.json"))).hasMessage("CONTROL_FILE_MISSING");
        Path huge=f.directory.resolve("huge.json"); Files.write(huge,new byte[RecoveryControlJson.MAX_BYTES+1]);
        assertThatThrownBy(()->authority.material(huge)).hasMessage("RECOVERY_OPERATIONS_SIZE");
        Path alias=f.directory.resolve("alias.json"); Files.createLink(alias,f.identityFile);
        assertThatThrownBy(authority::requireUnchanged).hasMessage("CONTROL_FILE_ALIAS"); Files.delete(alias);
        byte[] bytes=(canonical(f.identity)+" ").getBytes(java.nio.charset.StandardCharsets.UTF_8); Files.write(f.identityFile,bytes);
        assertThatThrownBy(()->new RecoveryOperationsAuthority(f.closed,f.identityFile,sha256(bytes))).hasMessage("CONTROL_NONCANONICAL_JSON");
    }
    @Test void rejectsWrongIdentityDomainInstallationAndWarehouses() throws Exception {
        var f=RecoveryOperationsFixture.create(); var original=f.identity.deepCopy();
        f.identity.put("authorityKind","SERVER_RECEIPT"); f.publish();
        assertThatThrownBy(f::authority).hasMessage("RECOVERY_OPERATIONS_AUTHORITY_KIND"); f.identity.setAll(original);
        f.identity.put("installationId",UUID.randomUUID().toString()); f.publish();
        assertThatThrownBy(f::authority).hasMessage("RECOVERY_OPERATIONS_INSTALLATION"); f.identity.setAll(original);
        f.identity.putArray("warehouseIds"); f.publish();
        assertThatThrownBy(f::authority).hasMessage("RECOVERY_OPERATIONS_WAREHOUSES");
        f.identity.putArray("warehouseIds").add(f.closed.sourceIdentity().warehouseId().toString()).add(f.closed.sourceIdentity().warehouseId().toString()); f.publish();
        assertThatThrownBy(f::authority).hasMessage("RECOVERY_OPERATIONS_WAREHOUSE_ORDER");
    }
    @Test void rejectsWeakWrongTypeAndNoncanonicalKeys() throws Exception {
        var f=RecoveryOperationsFixture.create();
        for(String type:List.of("RSA","EC")) {
            var generator=java.security.KeyPairGenerator.getInstance(type); generator.initialize(type.equals("RSA")?1024:256);
            f.identity.put("publicKeyDer",Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded())); f.publish();
            assertThatThrownBy(f::authority).hasMessage("RECOVERY_OPERATIONS_KEY_INVALID");
        }
        f.identity.put("publicKeyDer",Base64.getEncoder().encodeToString(f.key.getPublic().getEncoded())+"\n"); f.publish();
        assertThatThrownBy(f::authority).hasMessage("RECOVERY_OPERATIONS_KEY_INVALID");
    }
}
