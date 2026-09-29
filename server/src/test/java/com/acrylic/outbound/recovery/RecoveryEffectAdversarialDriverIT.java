package com.acrylic.outbound.recovery;

import java.sql.*;
import java.util.stream.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;

/** Driver controls: these bypass the effect gate intentionally, never count as gate protection. */
class RecoveryEffectAdversarialDriverIT {
    @BeforeAll static void environment() throws Exception { EffectRealEnvironment.requireEnvironment(); }
    static Stream<Arguments> mutations() {
        return IntStream.rangeClosed(1, 3).boxed().flatMap(n -> Stream.of("blob-set", "blob-truncate",
            "clob-set", "clob-truncate", "xml-set").map(m -> Arguments.of(n, m)));
    }

    @ParameterizedTest @MethodSource("mutations")
    void driverValueMutationHasMeasuredPersistence(int repeat, String operation) throws Exception {
        try (var f = new EffectAdversarialFixture(false, true); var rows = f.rows()) {
            if (operation.equals("blob-set")) rows.getBlob(2).setBytes(1, new byte[]{88});
            if (operation.equals("blob-truncate")) rows.getBlob(2).truncate(0);
            if (operation.equals("clob-set")) rows.getClob(3).setString(1, "X");
            if (operation.equals("clob-truncate")) rows.getClob(3).truncate(1);
            if (operation.equals("xml-set")) rows.getObject(3, SQLXML.class).setString("<changed/>");
            String expected = operation.equals("blob-set") ? "1:58:original;"
                : operation.equals("blob-truncate") ? "1::original;" : "1:62:original;";
            f.record("driver-mutation-" + operation + "|repeat=" + repeat + "|emulateLocators=true|gate=OFF-control");
            assertThat(f.observed()).isEqualTo(expected);
            assertThat(f.authority.checks).isZero();
        }
    }

    @RepeatedTest(3) void plainGetObjectReturnsDetachedBytes() throws Exception {
        try (var f = new EffectAdversarialFixture(false, true); var rows = f.rows()) {
            Object value = rows.getObject(2);
            assertThat(value).isInstanceOf(byte[].class);
            ((byte[]) value)[0] = 88;
            assertThat(f.observed()).isEqualTo("1:62:original;");
            f.record("driver-plainObject|class=byte[]|mutatedLocal=true|committedDelta=0");
        }
    }

    @RepeatedTest(3) void connectionCreatedValuesAreDetachedOnMeasuredDriver() throws Exception {
        try (var f = new EffectAdversarialFixture(true, false)) {
            Blob b = f.connection.createBlob(); Clob c = f.connection.createClob();
            SQLXML x = f.connection.createSQLXML();
            f.gate.fence();
            b.setBytes(1, new byte[]{88}); b.truncate(0);
            c.setString(1, "X"); c.truncate(0); x.setString("<changed/>");
            assertThat(f.observed()).isEqualTo("1:62:original;");
            f.record("connection-created-values|beforeFence=true|mutatedAfter=true|detachedOnThisDriver=true|committedDelta=0");
        }
    }
}
