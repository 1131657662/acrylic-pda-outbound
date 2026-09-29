package com.acrylic.outbound.recovery;

import java.sql.*;
import java.util.stream.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;

class RecoveryEffectAdversarialDefaultOffIT {
    @BeforeAll static void environment() throws Exception { EffectRealEnvironment.requireEnvironment(); }
    static Stream<Arguments> paths() {
        return IntStream.rangeClosed(1, 3).boxed().flatMap(n -> Stream.of("comment", "multi", "session",
            "autoCommit", "ddl").map(path -> Arguments.of(n, path)));
    }

    @ParameterizedTest @MethodSource("paths")
    void absentSwitchPreservesRealLegacyBehavior(int repeat, String path) throws Exception {
        assertThat(new RecoveryEffectConfiguration.Settings().isEnabled()).isFalse();
        try (var f = new EffectAdversarialFixture(false, false); var s = f.connection.createStatement()) {
            f.gate.fence(); f.authority.denial = "MODEL-default-off-must-not-query";
            switch (path) {
                case "comment" -> s.execute("/* allowed by legacy */ " + f.writeSql());
                case "multi" -> s.execute(f.writeSql() + ";SELECT 1");
                case "session" -> {
                    s.execute("SET SESSION sql_mode='ANSI'");
                    try (var r = s.executeQuery("SELECT @@SESSION.sql_mode")) {
                        assertThat(r.next()).isTrue(); assertThat(r.getString(1)).contains("ANSI");
                    }
                }
                default -> implicitCommit(f, s, path);
            }
            assertThat(f.authority.checks).isZero();
            assertThat(f.observed()).isEqualTo(path.equals("session") ? "1:62:original;" : "1:62:original;2:62:new;");
            f.record("default-off-" + path + "|repeat=" + repeat + "|legacyConstructor=true|checks=0");
        }
    }

    private static void implicitCommit(EffectAdversarialFixture f, Statement s, String path) throws SQLException {
        f.connection.setAutoCommit(false); s.executeUpdate(f.writeSql());
        assertThat(f.observed()).isEqualTo("1:62:original;");
        if (path.equals("autoCommit")) f.connection.setAutoCommit(true);
        else { s.execute("CREATE TABLE d_" + f.table + "(id INT)"); f.connection.rollback(); }
    }
}
