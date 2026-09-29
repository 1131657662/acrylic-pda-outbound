package com.acrylic.outbound.recovery;

import java.sql.*;
import java.util.*;
import java.util.stream.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecoveryEffectAdversarialAcquisitionTest {
    static Stream<Arguments> getters() {
        return IntStream.rangeClosed(1, 3).boxed().flatMap(n -> Stream.of(ResultSet.class, CallableStatement.class)
            .flatMap(api -> Arrays.stream(api.getMethods()).filter(m -> Set.of("getObject", "getBlob",
                "getClob", "getNClob", "getSQLXML", "getArray", "getRef").contains(m.getName()))
                .map(m -> Arguments.of(n, api, m))));
    }

    @ParameterizedTest(name = "{0}: {1} {2}") @MethodSource("getters")
    void allOpaqueValueOverloadsRefuseBeforeDelegate(int repeat, Class<?> api, java.lang.reflect.Method method)
            throws Exception {
        try (var f = new EffectUnitFixture(true)) {
            Object raw = mock(api);
            var owner = (RecoveryConnectionHandler) java.lang.reflect.Proxy.getInvocationHandler(f.connection);
            Object proxy = RecoveryJdbcProxy.wrap(raw, f.connection, owner);
            Object[] args = Arrays.stream(method.getParameterTypes()).map(RecoveryEffectAdversarialAcquisitionTest::argument)
                .toArray();
            assertThatThrownBy(() -> method.invoke(proxy, args))
                .hasRootCauseMessage("CONTROL_EFFECT_UNCLASSIFIED");
            verifyNoInteractions(raw);
        }
    }

    private static Object argument(Class<?> type) {
        if (type == int.class) return 1;
        if (type == String.class) return "b";
        if (type == Class.class) return Blob.class;
        if (type == Map.class) return Map.of();
        throw new AssertionError(type);
    }
}
