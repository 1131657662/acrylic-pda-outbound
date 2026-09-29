package com.acrylic.outbound.recovery;

import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.stream.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecoveryEffectAdversarialSurfaceTest {
    static Stream<Arguments> surface() {
        return IntStream.rangeClosed(1, 3).boxed().flatMap(n -> Stream.of(Statement.class, PreparedStatement.class,
            CallableStatement.class, ResultSet.class).flatMap(api -> Arrays.stream(api.getMethods())
                .map(method -> Arguments.of(n, api, method))));
    }

    @ParameterizedTest(name = "{0}: {1} {2}") @MethodSource("surface")
    void everyStandardMethodHasAnExplicitDecisionAndDelegateCount(int repeat, Class<?> api, Method method) throws Exception {
        String expected = EffectJdbcSurface.decision(api, method);
        try (var f = new EffectUnitFixture(true)) {
            Object raw = mock(api);
            var owner = (RecoveryConnectionHandler) Proxy.getInvocationHandler(f.connection);
            Object object = api == ResultSet.class ? RecoveryJdbcProxy.wrap(raw, f.connection, owner)
                : RecoveryJdbcProxy.prepared(raw, f.connection, owner, "INSERT INTO t VALUES(1)");
            f.gate.fence();
            Object[] args = Arrays.stream(method.getParameterTypes()).map(RecoveryEffectAdversarialSurfaceTest::argument).toArray();
            if (method.getName().startsWith("execute") || method.getName().equals("addBatch"))
                if (args.length > 0) args[0] = "INSERT INTO t VALUES(1)";
            Throwable failure = catchThrowable(() -> method.invoke(object, args));
            boolean deny = expected.startsWith("DENY") || Set.of("CHECK_EFFECT", "SQL_OR_BATCH").contains(expected);
            if (deny) {
                assertThat(failure).hasRootCauseInstanceOf(SQLException.class);
                verifyNoInteractions(raw);
            } else {
                assertThat(failure).isNull();
                assertThat(mockingDetails(raw).getInvocations()).hasSize(expected.equals("PROXY_ONLY") ? 0 : 1);
            }
            System.out.println("MODEL|API|" + api.getSimpleName() + "|" + method.toGenericString()
                + "|decision=" + expected + "|delegate=" + mockingDetails(raw).getInvocations().size() + "|repeat=" + repeat);
        }
    }

    private static Object argument(Class<?> type) {
        if (type == String.class) return "column";
        if (type == Class.class) return Object.class;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 1;
        if (type == short.class) return (short) 1;
        if (type == int.class) return 1;
        if (type == long.class) return 1L;
        if (type == float.class) return 1F;
        if (type == double.class) return 1D;
        return null;
    }
}
