package com.acrylic.outbound.e2averify;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure guard tests; no datasource, process, or filesystem initialization. */
class E2aMigrationEnvironmentTest {
    private static final Path WORKSPACE = Path.of("D:/acrylic-pda-worktrees/r06-e2a-migration");

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
}
