package com.acrylic.outbound.recovery;

import java.sql.*;
import java.util.stream.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;

class RecoveryEffectAdversarialFenceIT {
    @BeforeAll static void environment() throws Exception { EffectRealEnvironment.requireEnvironment(); }
    static Stream<Arguments> paths() {
        return IntStream.rangeClosed(1, 3).boxed().flatMap(n -> Stream.of("insertRow", "updateRow", "deleteRow",
            "updateString", "moveToInsertRow").map(path -> Arguments.of(n, path)));
    }

    @ParameterizedTest @MethodSource("paths")
    void preexistingCursorSeesFencedFlagBeforeEachMutator(int repeat, String path) throws Exception {
        try (var f = new EffectAdversarialFixture(true, false); var r = f.rows()) {
            if (path.equals("insertRow")) { r.moveToInsertRow(); r.updateInt(1, 2); }
            r.updateString(3, "changed");
            int before = f.calls.count("ResultSet." + path);
            f.gate.fence();
            assertThatThrownBy(() -> {
                if (path.equals("updateString")) r.updateString(3, "again");
                else ResultSet.class.getMethod(path).invoke(r);
            }).satisfies(error -> {
                Throwable cause = error instanceof java.lang.reflect.InvocationTargetException ? error.getCause() : error;
                assertThat(cause).hasMessage("RECOVERY_LEASE_FENCED");
            });
            assertThat(f.calls.count("ResultSet." + path)).isEqualTo(before);
            assertThat(f.observed()).isEqualTo("1:62:original;");
            f.record("preexisting-cursor-" + path + "|repeat=" + repeat + "|delegateDelta=0");
        }
    }
}
