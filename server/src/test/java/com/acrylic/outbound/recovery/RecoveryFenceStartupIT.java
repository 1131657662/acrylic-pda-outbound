package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.stream.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class RecoveryFenceStartupIT {
    static Path evidence;
    @BeforeAll static void environment() throws Exception {
        RecoveryFenceCrossProcessIT.environment(); evidence = RecoveryFenceCrossProcessIT.evidence;
    }
    static Stream<Arguments> inputs() {
        return IntStream.range(0, 3).boxed().flatMap(i -> Stream.of("identityMissing", "identityChanged", "markerMissing", "markerChanged", "fenceMissing")
            .map(kind -> Arguments.of(i, kind)));
    }
    @ParameterizedTest @MethodSource("inputs")
    void unavailableOrChangedAuthorityCannotStart(int repeat, String kind) throws Exception {
        var supply = FenceTestSupply.create(evidence);
        Path identity = supply.roots().authorityRoot().resolve("identity.json");
        Path marker = supply.roots().root().resolve("lease-root.json");
        String code;
        switch (kind) {
            case "identityMissing" -> { Files.move(identity, supply.base().resolve("original-identity")); code = "CONTROL_FILE_MISSING"; }
            case "identityChanged" -> { mutate(identity, "initialCommandSha256"); code = "CONTROL_AUTHORITY_IDENTITY_CHANGED"; }
            case "markerMissing" -> { Files.move(marker, supply.base().resolve("original-marker")); code = "CONTROL_FILE_MISSING"; }
            case "markerChanged" -> { mutate(marker, "bindingSha256"); code = "RECOVERY_LEASE_ROOT_UNBOUND"; }
            case "fenceMissing" -> {
                Files.move(supply.roots().root().resolve("fenced.lock"), supply.base().resolve("original-fence"));
                code = "CONTROL_FENCE_LOCK_MISSING";
            }
            default -> throw new AssertionError(kind);
        }
        assertThatThrownBy(() -> RecoveryEffectGate.enabled(supply.authority())).hasMessage(code);
        try (var processes = Files.list(supply.roots().root().resolve("processes"))) { assertThat(processes.count()).isZero(); }
        EffectRealEnvironment.record("REAL|startup-refused|case=" + kind + "|code=" + code + "|no-process-record|no-database-delegate");
    }
    private static void mutate(Path path, String field) throws Exception {
        var value = (com.fasterxml.jackson.databind.node.ObjectNode)RecoveryControlHistory.read(path);
        value.put(field, "b".repeat(64));
        Files.writeString(path, RecoveryControlFormat.canonical(value));
    }
    @RepeatedTest(3) void explicitFlagsAndRealAuthorityCreateGateWithoutDatasource() throws Exception {
        var supply = FenceTestSupply.create(evidence);
        new ApplicationContextRunner().withUserConfiguration(RecoveryEffectConfiguration.class)
            .withBean(RecoveryEffectGate.Authority.class, supply::authority)
            .withPropertyValues("outbound.recovery.effect-gate.enabled=true", "outbound.recovery.effect-gate.cross-process-enabled=true")
            .run(context -> {
                assertThat(context).hasNotFailed(); assertThat(context.getBean(RecoveryEffectGate.class).enabled()).isTrue();
                assertThat(context.getBeansOfType(javax.sql.DataSource.class)).isEmpty();
            });
    }
}
