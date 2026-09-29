package com.acrylic.outbound.recovery;

import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.stream.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;

class RecoveryEffectAdversarialHandlesIT {
    @BeforeAll static void environment() throws Exception { EffectRealEnvironment.requireEnvironment(); }
    static Stream<Arguments> routes() {
        return IntStream.rangeClosed(1, 3).boxed().flatMap(n -> Stream.of("statement", "resultset", "chain",
            "metadata", "credentials").map(route -> Arguments.of(n, route)));
    }

    @ParameterizedTest @MethodSource("routes")
    void returnedHandleRemainsInsideFencedGate(int repeat, String route) throws Exception {
        try (var f = new EffectAdversarialFixture(true, false)) {
            var s = f.connection.createStatement();
            var r = s.executeQuery("SELECT id FROM " + f.table);
            Connection recovered = switch (route) {
                case "statement" -> s.getConnection();
                case "resultset", "chain" -> r.getStatement().getConnection();
                case "metadata" -> f.connection.getMetaData().getConnection();
                case "credentials" -> f.adapter.getConnection(EffectRealEnvironment.USER, System.getenv("OUTBOUND_DB_PASSWORD"));
                default -> throw new AssertionError(route);
            };
            assertThat(Proxy.isProxyClass(recovered.getClass())).isTrue();
            assertThat(Proxy.isProxyClass(r.getStatement().getClass())).isTrue();
            if (!route.equals("credentials")) assertThat(recovered).isSameAs(f.connection);
            int before = f.calls.count("Statement.executeUpdate");
            f.gate.fence();
            assertThatThrownBy(() -> (route.equals("resultset") ? r.getStatement() : recovered.createStatement())
                .executeUpdate(f.writeSql()))
                .hasMessage("RECOVERY_LEASE_FENCED");
            assertThat(f.calls.count("Statement.executeUpdate")).isEqualTo(before);
            assertThat(f.observed()).isEqualTo("1:62:original;");
            f.record("handle-" + route + "|repeat=" + repeat + "|writeDelegateDelta=0");
            if (route.equals("credentials")) recovered.close();
        }
    }

    static Stream<Arguments> reverseExecutions() {
        return IntStream.rangeClosed(1, 3).boxed().flatMap(n -> Stream.of("execute", "executeQuery",
            "executeUpdate", "executeLargeUpdate", "addBatch", "executeBatch", "executeLargeBatch")
            .map(method -> Arguments.of(n, method)));
    }

    @ParameterizedTest @MethodSource("reverseExecutions")
    void reverseReferenceReusesPreparedProxyAndSqlMemory(int repeat, String method) throws Exception {
        try (var f = new EffectAdversarialFixture(true, false)) {
            var s = f.connection.prepareStatement("SELECT id FROM " + f.table);
            var r = s.executeQuery();
            assertThat(r.getStatement()).isSameAs(s);
            var recovered = (PreparedStatement) r.getStatement();
            f.gate.fence();
            int before = f.calls.count("PreparedStatement." + method);
            assertThatThrownBy(() -> PreparedStatement.class.getMethod(method).invoke(recovered))
                .hasRootCauseMessage("RECOVERY_LEASE_FENCED");
            assertThat(f.calls.count("PreparedStatement." + method)).isEqualTo(before);
            assertThat(f.calls.count("PreparedStatement.executeQuery")).isEqualTo(1);
            assertThat(f.observed()).isEqualTo("1:62:original;");
            f.record("reverse-prepared-memory|repeat=" + repeat + "|method=" + method
                + "|identity=true|deniedDelegateDelta=0");
        }
    }

    @RepeatedTest(3) void connectionCredentialOverloadDoesNotExistInJdbc() {
        assertThatThrownBy(() -> Connection.class.getMethod("getConnection", String.class, String.class))
            .isInstanceOf(NoSuchMethodException.class);
    }

    static Stream<Arguments> wrappers() {
        return IntStream.rangeClosed(1, 3).boxed().flatMap(n -> Stream.of("connection", "statement", "prepared",
            "callable", "resultset", "metadata").flatMap(kind -> Stream.of(Connection.class, Statement.class,
                PreparedStatement.class, CallableStatement.class, ResultSet.class, DatabaseMetaData.class,
                Wrapper.class, Object.class).map(type -> Arguments.of(n, kind, type))));
    }

    @ParameterizedTest @MethodSource("wrappers")
    void unwrapNeverReturnsPhysicalObjectIncludingCrossInterfaces(int repeat, String kind, Class<?> type) throws Exception {
        try (var f = new EffectAdversarialFixture(true, false)) {
            Wrapper object = wrapper(f, kind);
            f.gate.fence();
            boolean compatible = type.isInstance(object);
            assertThat(object.isWrapperFor(type)).isEqualTo(compatible);
            if (compatible) assertThat(object.unwrap(type)).isSameAs(object);
            else assertThatThrownBy(() -> object.unwrap(type)).isInstanceOf(SQLException.class);
            Class<?> vendor = Class.forName("com.mysql.cj.jdbc.JdbcConnection");
            assertThat(object.isWrapperFor(vendor)).isFalse();
            assertThatThrownBy(() -> object.unwrap(vendor)).isInstanceOf(SQLException.class);
            for (String api : new String[]{"Connection", "Statement", "PreparedStatement", "CallableStatement",
                    "ResultSet", "DatabaseMetaData"}) {
                assertThat(f.calls.count(api + ".unwrap")).isZero();
                assertThat(f.calls.count(api + ".isWrapperFor")).isZero();
            }
            Connection recovered = owner(object);
            assertThatThrownBy(() -> recovered.createStatement().executeUpdate(f.writeSql()))
                .hasMessage("RECOVERY_LEASE_FENCED");
            assertThat(f.calls.count("Statement.executeUpdate")).isZero();
            assertThat(f.observed()).isEqualTo("1:62:original;");
            f.record("unwrap|repeat=" + repeat + "|kind=" + kind + "|target=" + type.getSimpleName()
                + "|sameProxy=" + compatible + "|vendorDenied=true");
        }
    }

    private static Connection owner(Wrapper object) throws SQLException {
        if (object instanceof Connection c) return c;
        if (object instanceof Statement s) return s.getConnection();
        if (object instanceof ResultSet r) return r.getStatement().getConnection();
        return ((DatabaseMetaData) object).getConnection();
    }

    private static Wrapper wrapper(EffectAdversarialFixture f, String kind) throws SQLException {
        return switch (kind) {
            case "connection" -> f.connection;
            case "statement" -> f.connection.createStatement();
            case "prepared" -> f.connection.prepareStatement("SELECT 1");
            case "callable" -> f.connection.prepareCall("SELECT 1");
            case "resultset" -> f.rows();
            case "metadata" -> f.connection.getMetaData();
            default -> throw new AssertionError(kind);
        };
    }
}
