package com.acrylic.outbound.e2averify;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class E2aMysqlBootstrapTest {
    @Test
    void generatesIndependentHexPasswords() {
        String first = E2aMysqlBootstrap.newPassword();
        String second = E2aMysqlBootstrap.newPassword();

        assertThat(first).matches("[0-9a-f]{64}").isNotEqualTo(second);
    }

    @Test
    void restrictsBootstrapToDedicatedSchemaAndAccount() {
        String sql = E2aMysqlBootstrap.sql("a".repeat(64));

        assertThat(sql).contains("CREATE DATABASE `" + E2aMigrationEnvironment.DATABASE + "`;")
            .contains("GRANT SHUTDOWN ON *.* TO '" + E2aMigrationEnvironment.USER + "'@'127.0.0.1'")
            .doesNotContain("root", "IF NOT EXISTS");
    }

    @Test
    void rejectsUnvalidatedPasswordBeforeCreatingArtifacts() {
        Path workspace = E2aMigrationRunner.workspace();

        assertThatThrownBy(() -> E2aMysqlBootstrap.write(workspace, "not-hex"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
