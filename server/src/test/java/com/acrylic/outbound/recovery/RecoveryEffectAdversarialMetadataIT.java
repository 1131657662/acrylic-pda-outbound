package com.acrylic.outbound.recovery;

import java.sql.*;
import java.util.stream.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;

/** Connector/J client prepared metadata may execute the original SQL to discover its columns. */
class RecoveryEffectAdversarialMetadataIT {
    @BeforeAll static void environment() throws Exception { EffectRealEnvironment.requireEnvironment(); }

    static Stream<Arguments> routes() {
        return IntStream.rangeClosed(1, 3).boxed().flatMap(n -> Stream.of("prepared", "callable")
            .flatMap(kind -> Stream.of(false, true).map(fenced -> Arguments.of(n, kind, fenced))));
    }

    @ParameterizedTest @MethodSource("routes")
    void metadataAcquisitionMustNotExecuteSql(int repeat, String kind, boolean fenced) throws Exception {
        try (var f = new EffectAdversarialFixture(true, false)) {
            String sql = mutatingSelect(f);
            PreparedStatement s = kind.equals("prepared") ? f.connection.prepareStatement(sql)
                : f.connection.prepareCall(sql);
            if (fenced) f.gate.fence();
            Throwable failure = catchThrowable(s::getMetaData);
            String call = kind.equals("prepared") ? "PreparedStatement.getMetaData" : "CallableStatement.getMetaData";
            f.record("metadata-execution|repeat=" + repeat + "|kind=" + kind + "|fenced=" + fenced
                + "|delegate=" + f.calls.count(call) + "|failure=" + failure);
            assertThat(failure).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
            assertThat(f.calls.count(call)).isZero();
            assertThat(f.observed()).isEqualTo("1:62:original;");
        }
    }

    @RepeatedTest(3) void defaultOffMetadataControlReallyExecutesFunction() throws Exception {
        try (var f = new EffectAdversarialFixture(false, false)) {
            try (var s = f.connection.prepareStatement(mutatingSelect(f))) {
                assertThat(s.getMetaData().getColumnCount()).isEqualTo(1);
            }
            assertThat(f.observed()).isEqualTo("1:62:original;2:62:new;");
            assertThat(f.authority.checks).isZero();
            f.record("metadata-driver-control|getMetaDataDelegate=1|committedDelta=1|gate=OFF");
        }
    }

    private static String mutatingSelect(EffectAdversarialFixture f) throws SQLException {
        try (var c = EffectRealEnvironment.open(false); var s = c.createStatement()) {
            s.execute("CREATE FUNCTION fm_" + f.table + "() RETURNS INT MODIFIES SQL DATA BEGIN "
                + f.writeSql() + "; RETURN 1; END");
        }
        return "SELECT fm_" + f.table + "()";
    }
}
