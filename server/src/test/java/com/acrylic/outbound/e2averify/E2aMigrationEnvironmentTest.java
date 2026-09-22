package com.acrylic.outbound.e2averify;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure guard tests; no datasource, process, or filesystem initialization. */
class E2aMigrationEnvironmentTest {
    private static final Path WORKSPACE = Path.of("D:/acrylic-pda-worktrees/r06-e2a-migration");

    private static Map<String, String> valid() {
        var env = new HashMap<>(Map.of(
            "E2A_MIGRATION_VERIFY", "true",
            "OUTBOUND_MIGRATE", "true",
            "OUTBOUND_DB_URL", E2aMigrationEnvironment.URL,
            "OUTBOUND_DB_USER", E2aMigrationEnvironment.USER));
        env.put("OUTBOUND_DATADIR", WORKSPACE.resolve(E2aMigrationEnvironment.DATA_DIR).toString());
        env.put("OUTBOUND_STORAGE", WORKSPACE.resolve(E2aMigrationEnvironment.STORAGE_DIR).toString());
        env.put("OUTBOUND_AUTH_SIGNING_PRIVATE_KEY", WORKSPACE.resolve(E2aMigrationEnvironment.SIGNING_DIR).resolve("private.der").toString());
        env.put("OUTBOUND_AUTH_SIGNING_PUBLIC_KEY", WORKSPACE.resolve(E2aMigrationEnvironment.SIGNING_DIR).resolve("public.der").toString());
        return env;
    }

    @Test
    void acceptsExactPortAndWorkspace() {
        assertThatCode(() -> E2aMigrationEnvironment.requireExact(true, WORKSPACE))
            .doesNotThrowAnyException();
    }

    @Test
    void rejectsOccupiedPortBeforeAnyDatabaseAction() {
        assertThatThrownBy(() -> E2aMigrationEnvironment.requireExact(false, WORKSPACE))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsAnotherWorktreeBeforeAnyDatabaseAction() {
        assertThatThrownBy(() -> E2aMigrationEnvironment.requireExact(true,
                Path.of("D:/acrylic-pda-outbound")))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsExactConnectionAndPathProfile() {
        assertThatCode(() -> E2aMigrationEnvironment.requireExact(valid(), true, WORKSPACE))
            .doesNotThrowAnyException();
    }

    @Test
    void rejectsSchemaPortOrAccountSubstitution() {
        var env = valid();
        env.put("OUTBOUND_DB_URL", E2aMigrationEnvironment.URL.replace("19329", "3306"));
        assertThatThrownBy(() -> E2aMigrationEnvironment.requireExact(env, true, WORKSPACE))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsPathTraversalConfiguration() {
        var env = valid();
        env.put("OUTBOUND_STORAGE", WORKSPACE.resolve(".local/../outside").toString());
        assertThatThrownBy(() -> E2aMigrationEnvironment.requireExact(env, true, WORKSPACE))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsResetOrRepairFlags() {
        var env = valid();
        env.put("E2A_MIGRATION_RESET", "true");
        assertThatThrownBy(() -> E2aMigrationEnvironment.requireExact(env, true, WORKSPACE))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsSigningPathSubstitution() {
        var env = valid();
        env.put("OUTBOUND_AUTH_SIGNING_PUBLIC_KEY", WORKSPACE.resolve(".local/other.der").toString());
        assertThatThrownBy(() -> E2aMigrationEnvironment.requireExact(env, true, WORKSPACE))
            .isInstanceOf(IllegalStateException.class);
    }
}
