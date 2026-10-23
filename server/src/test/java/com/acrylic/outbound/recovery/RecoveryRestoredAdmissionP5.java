package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Explicit test-only supply after database registration; preserves both replaced original bytes. */
public final class RecoveryRestoredAdmissionP5 {
    private final Path home;
    private final RecoveryControlLocation location;
    private final RecoveryInitialAuthorityProvision.Provisioned pin;

    RecoveryRestoredAdmissionP5(RecoveryControlLocation location, Path attempt) throws Exception {
        this.location = location;
        home = Files.createDirectory(attempt.resolve("private/restored-admission-p5"));
        var before = hashes(location.root());
        for (String name : List.of("identity.json", "writers.json"))
            Files.copy(location.root().resolve(name), home.resolve("before-" + name));
        pin = RecoveryInitialAuthorityProvision.provision(location.root(), home.resolve("p5"), home.resolve("closed"));
        var descriptor = (ObjectNode) RecoveryControlHistory.read(location.root().resolve("writers.json"));
        descriptor.put("identitySha256", pin.identitySha256());
        RecoveryControlFixture.write(location.root().resolve("writers.json"), descriptor);
        var after = hashes(location.root());
        assertThat(after.keySet()).isEqualTo(before.keySet());
        assertThat(after.keySet().stream().filter(key -> !after.get(key).equals(before.get(key))).toList())
            .containsExactlyInAnyOrder("identity.json", "writers.json");
        for (String name : List.of("identity.json", "writers.json"))
            Files.copy(location.root().resolve(name), home.resolve("after-" + name));
        save("promotion.json", Map.of("syntheticTestOnly", true, "productProvisioning", false,
            "before", before, "after", after, "identitySha256", pin.identitySha256(), "markerSha256", pin.markerSha256()));
        roots().verify();
        save("p5-before.json", hashes(home.resolve("p5")));
    }

    String[] options() {
        return new String[]{"--outbound.recovery.lease.enabled=true",
            "--outbound.recovery.lease.root=" + home.resolve("p5"),
            "--outbound.recovery.lease.closed-authority=" + home.resolve("closed"),
            "--outbound.recovery.lease.identity-sha256=" + pin.identitySha256(),
            "--outbound.recovery.lease.marker-sha256=" + pin.markerSha256(),
            "--outbound.recovery.effect-gate.enabled=true", "--outbound.recovery.effect-gate.cross-process-enabled=true",
            "--outbound.recovery.effect-gate.anchor=" + RecoveryControlFormat.hex(pin.anchor())};
    }

    public void finish() throws Exception {
        roots().verify();
        JsonNode index = RecoveryControlHistory.read(home.resolve("p5/leases/index.json"));
        RecoveryControlFormat.anchorTag(pin.anchor(), index, "indexTag");
        assertThat(index.path("entries")).isEmpty();
        try (var files = Files.list(home.resolve("p5/leases"))) {
            assertThat(files.map(path -> path.getFileName().toString()).toList()).containsExactly("index.json");
        }
        save("p5-final.json", hashes(home.resolve("p5")));
        save("finished.json", Map.of("rootAndIndexCheck", true, "emptyLeaseIndex", true, "syntheticTestOnly", true));
    }

    private RecoveryLeaseRoots roots() {
        var binding = new RecoveryLeaseRoots.Binding(location, home.resolve("p5"), home.resolve("closed"), pin.identitySha256());
        return new RecoveryLeaseRoots(binding, pin.markerSha256(), pin.anchor());
    }

    private static Map<String, String> hashes(Path root) throws Exception {
        var result = new TreeMap<String, String>();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList())
                result.put(root.relativize(file).toString().replace('\\', '/'), RecoveryControlFormat.sha256(Files.readAllBytes(file)));
        }
        return result;
    }

    private void save(String name, Object value) throws Exception {
        Files.writeString(home.resolve(name), RecoveryControlFixture.JSON.writeValueAsString(value), StandardOpenOption.CREATE_NEW);
    }
}
