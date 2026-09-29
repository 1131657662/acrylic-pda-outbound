package com.acrylic.outbound.recovery;

import java.sql.*;
import java.util.stream.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;

class RecoveryEffectAdversarialCallableIT {
    @BeforeAll static void environment() throws Exception { EffectRealEnvironment.requireEnvironment(); }
    static Stream<Arguments> getters() {
        return IntStream.rangeClosed(1, 3).boxed().flatMap(n ->
            Stream.of(Blob.class, Clob.class, NClob.class, SQLXML.class, Array.class, Ref.class, Object.class)
                .flatMap(type -> Stream.of(false, true).map(named -> Arguments.of(n, type, named))));
    }

    @ParameterizedTest @MethodSource("getters")
    void registeredOutObjectCannotEscapeEvenWithoutExecutingCall(int repeat, Class<?> type, boolean named) throws Exception {
        try (var f = new EffectAdversarialFixture(true, false)) {
            procedure(f);
            var s = f.connection.prepareCall("{call p_" + f.table + "(?)}");
            s.registerOutParameter(1, Types.LONGVARCHAR);
            f.gate.fence();
            assertThatThrownBy(s::execute).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
            assertThatThrownBy(() -> object(s, type, named)).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
            assertThat(f.calls.count("CallableStatement.getObject")).isZero();
            assertThat(f.calls.count("CallableStatement.execute")).isZero();
            assertThat(f.observed()).isEqualTo("1:62:original;");
            f.record("callable-OUT|repeat=" + repeat + "|type=" + type.getSimpleName()
                + "|named=" + named + "|executeDelta=0|getObjectDelta=0");
        }
    }

    @RepeatedTest(3) void defaultOffProcedureReallyReturnsOutValue() throws Exception {
        try (var f = new EffectAdversarialFixture(false, false)) {
            procedure(f);
            var s = f.connection.prepareCall("{call p_" + f.table + "(?)}");
            s.registerOutParameter(1, Types.LONGVARCHAR); s.execute();
            assertThat(s.getObject(1)).isEqualTo("original");
            assertThat(s.getObject("out_value")).isEqualTo("original");
            assertThat(f.authority.checks).isZero();
            f.record("callable-OUT-driver-control|registered=true|executed=true|value=original");
        }
    }

    private static Object object(CallableStatement s, Class<?> type, boolean named) throws SQLException {
        if (type == Object.class) return named ? s.getObject("out_value") : s.getObject(1);
        return named ? s.getObject("out_value", type) : s.getObject(1, type);
    }

    private static void procedure(EffectAdversarialFixture f) throws SQLException {
        try (var c = EffectRealEnvironment.open(false); var s = c.createStatement()) {
            s.execute("CREATE PROCEDURE p_" + f.table + "(OUT out_value TEXT) SET out_value='original'");
        }
    }
}
