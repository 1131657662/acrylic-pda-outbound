package com.acrylic.outbound.recovery;

import java.nio.file.Path;
import java.util.stream.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;

/** Each connection/child entry independently makes its first refusal on the cross-process layer. */
class RecoveryFenceConnectionIT {
    static Path evidence;
    @BeforeAll static void environment() throws Exception {
        RecoveryFenceCrossProcessIT.environment(); evidence = RecoveryFenceCrossProcessIT.evidence;
    }
    static Stream<Arguments> entries() {
        return IntStream.range(0, 3).boxed().flatMap(i -> Stream.of("commit", "setAutoCommit", "prepared", "sourceRead")
            .map(entry -> Arguments.of(i, entry)));
    }
    @ParameterizedTest @MethodSource("entries")
    void firstCrossProcessRefusalIsBeforeEachDelegate(int repeat, String entry) throws Exception {
        var supply = FenceTestSupply.create(evidence);
        var gate = RecoveryEffectGate.enabled(supply.authority());
        try (var jdbc = new FenceRealJdbc(gate); var statement = jdbc.connection.createStatement();
                var prepared = jdbc.connection.prepareStatement(jdbc.insert(2))) {
            jdbc.connection.setAutoCommit(false); statement.executeUpdate(jdbc.insert(1));
            try (var child = new FenceChild(supply, "signal")) {
                child.await("LOCKED");
                String method = switch (entry) {
                    case "prepared" -> "PreparedStatement.executeUpdate";
                    case "sourceRead" -> "Statement.executeQuery";
                    default -> "Connection." + entry;
                };
                int before = jdbc.calls.count(method);
                assertThatThrownBy(() -> invoke(entry, jdbc, statement, prepared)).hasMessage("RECOVERY_LEASE_FENCED");
                assertThat(jdbc.calls.count(method)).isEqualTo(before); assertThat(jdbc.count()).isZero();
                EffectRealEnvironment.record("REAL|first-cross-process-entry=" + entry + "|delegateDelta=0|observer=0|repeat=" + repeat);
            }
        }
    }
    private static void invoke(String entry, FenceRealJdbc jdbc, java.sql.Statement statement,
            java.sql.PreparedStatement prepared) throws Exception {
        switch (entry) {
            case "commit" -> jdbc.connection.commit();
            case "setAutoCommit" -> jdbc.connection.setAutoCommit(true);
            case "prepared" -> prepared.executeUpdate();
            case "sourceRead" -> statement.executeQuery("SELECT id FROM " + jdbc.table);
            default -> throw new AssertionError(entry);
        }
    }
}
