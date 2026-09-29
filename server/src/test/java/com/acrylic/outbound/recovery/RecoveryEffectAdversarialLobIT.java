package com.acrylic.outbound.recovery;

import java.sql.*;
import java.util.stream.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;

class RecoveryEffectAdversarialLobIT {
    @BeforeAll static void environment() throws Exception { EffectRealEnvironment.requireEnvironment(); }

    static Stream<Arguments> retrievals() {
        return IntStream.rangeClosed(1, 3).boxed().flatMap(repeat ->
            Stream.of(Blob.class, Clob.class, NClob.class, SQLXML.class, Array.class, Ref.class, Object.class)
                .flatMap(type -> Stream.of(false, true).map(named -> Arguments.of(repeat, type, named))));
    }

    @ParameterizedTest(name = "{0}: getObject {1} named={2}") @MethodSource("retrievals")
    void mutableAcquisitionMustBeRejectedBeforeDelegate(int repeat, Class<?> type, boolean named) throws Exception {
        try (var f = new EffectAdversarialFixture(true, true); var rows = f.rows()) {
            String before = f.observed();
            f.gate.fence();
            Throwable failure = catchThrowable(() -> {
                Object value = get(rows, type, named);
                mutate(value);
            });
            f.record("LOB-getObject|repeat=" + repeat + "|type=" + type.getSimpleName() + "|named=" + named
                + "|delegate=" + f.calls.count("ResultSet.getObject") + "|failure=" + failure);
            assertThat(failure).isInstanceOf(SQLException.class).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
            assertThat(f.calls.count("ResultSet.getObject")).isZero();
            assertThat(f.observed()).isEqualTo(before);
        }
    }

    private static Object get(ResultSet r, Class<?> type, boolean named) throws SQLException {
        if (type == Object.class) return named ? r.getObject("b") : r.getObject(2);
        return named ? r.getObject("b", type) : r.getObject(2, type);
    }

    static void mutate(Object value) throws SQLException {
        if (value instanceof Blob b) { b.setBytes(1, new byte[]{88}); b.truncate(1); }
        if (value instanceof Clob c) { c.setString(1, "X"); c.truncate(1); }
        if (value instanceof SQLXML x) x.setString("<changed/>");
    }

    @RepeatedTest(3) void realLocatorObtainedBeforeFenceMustNotEscape() throws Exception {
        try (var f = new EffectAdversarialFixture(true, true); var rows = f.rows()) {
            String before = f.observed();
            Throwable failure = catchThrowable(() -> {
                Blob blob = rows.getObject(2, Blob.class);
                f.gate.fence();
                blob.setBytes(1, new byte[]{88});
            });
            f.record("LOB-beforeFence|delegate=" + f.calls.count("ResultSet.getObject") + "|failure=" + failure);
            assertThat(failure).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
            assertThat(f.calls.count("ResultSet.getObject")).isZero();
            assertThat(f.observed()).isEqualTo(before);
        }
    }

    @RepeatedTest(3) void vendorTypeHintCannotExposeDatabaseLocatorBeforeFence() throws Exception {
        try (var f = new EffectAdversarialFixture(true, true); var rows = f.rows()) {
            Class<?> vendor = Class.forName("com.mysql.cj.jdbc.Blob");
            Throwable failure = catchThrowable(() -> {
                Object value = rows.getObject(2, vendor);
                f.gate.fence();
                ((Blob) value).setBytes(1, new byte[]{88});
            });
            f.record("vendor-LOB-beforeFence|delegate=" + f.calls.count("ResultSet.getObject")
                + "|setBytes=" + f.calls.count("Blob.setBytes") + "|failure=" + failure);
            assertThat(failure).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
            assertThat(f.calls.count("ResultSet.getObject")).isZero();
            assertThat(f.observed()).isEqualTo("1:62:original;");
        }
    }
}
