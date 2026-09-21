package com.acrylic.outbound.foundationmigration;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class FoundationEnvironmentTest {
    private Map<String, String> valid() {
        return new HashMap<>(Map.of("FOUNDATION_VERIFY", "true", "OUTBOUND_DB_USER", FoundationEnvironment.USER,
                "OUTBOUND_DB_URL", "jdbc:mysql://127.0.0.1:19324/" + FoundationEnvironment.DATABASE
                        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                "OUTBOUND_STORAGE", FoundationEnvironment.ROOT.resolve("private-files").toString()));
    }

    @Test void acceptsOnlyAssignedNamespace() {
        assertThatCode(() -> FoundationEnvironment.validate(valid())).doesNotThrowAnyException();
    }

    @ParameterizedTest @ValueSource(strings = {"FOUNDATION_VERIFY", "OUTBOUND_DB_USER", "OUTBOUND_DB_URL", "OUTBOUND_STORAGE"})
    void rejectsMissingGuardBeforeDatabaseInitialization(String field) {
        var env = valid();
        env.remove(field);
        assertThatThrownBy(() -> FoundationEnvironment.validate(env)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"13316", "19322", "19323", "3306"})
    void rejectsOtherPorts(String port) {
        var env = valid();
        env.put("OUTBOUND_DB_URL", env.get("OUTBOUND_DB_URL").replace("19324", port));
        assertThatThrownBy(() -> FoundationEnvironment.validate(env)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"dirty", "dirty2", "missing", "signature", "ddl", "lock"})
    void negativeNamespaceCannotOverwriteFrozenSourceEvidence(String mode) {
        var env = valid(); env.put("FOUNDATION_CASE", mode);
        env.put("OUTBOUND_DB_URL", env.get("OUTBOUND_DB_URL").replace("20260921?", "20260921_" + mode + "?"));
        env.put("OUTBOUND_DB_USER", "fndv_20260921_" + mode);
        env.put("OUTBOUND_STORAGE", FoundationEnvironment.ROOT.resolve("private-files_" + mode).toString());
        assertThatCode(() -> FoundationEnvironment.validate(env)).doesNotThrowAnyException();
        assertThatThrownBy(() -> FoundationEnvironment.requireSource(env)).isInstanceOf(IllegalStateException.class);
    }
}
