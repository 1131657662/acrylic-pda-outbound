package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class E2aMysqlTriggerPreflightTest {
    @TempDir Path directory;

    @Test
    void blocksUntrustedTriggerCreationAndRecordsConditions() throws Exception {
        JdbcTemplate jdbc = eligibleJdbc();
        when(jdbc.queryForObject("SELECT @@GLOBAL.log_bin_trust_function_creators", Integer.class)).thenReturn(0);
        Path evidence = directory.resolve("preflight.json");

        assertThatThrownBy(() -> E2aMysqlTriggerPreflight.check(jdbc, evidence))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("log_bin_trust_function_creators");

        assertThat(Files.readString(evidence)).contains("\"result\":\"blocked\"", "\"logBin\":1", "\"triggerGrants\":1");
    }

    @Test
    void rejectsDisabledBinaryLogging() throws Exception {
        JdbcTemplate jdbc = eligibleJdbc();
        when(jdbc.queryForObject("SELECT @@GLOBAL.log_bin", Integer.class)).thenReturn(0);

        assertThatThrownBy(() -> E2aMysqlTriggerPreflight.check(jdbc, directory.resolve("preflight.json")))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("binary logging must remain enabled");
    }

    @Test
    void rejectsGlobalSuperPrivilege() throws Exception {
        JdbcTemplate jdbc = eligibleJdbc();
        when(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.USER_PRIVILEGES WHERE GRANTEE=? AND PRIVILEGE_TYPE='SUPER'",
            Integer.class, grantee())).thenReturn(1);

        assertThatThrownBy(() -> E2aMysqlTriggerPreflight.check(jdbc, directory.resolve("preflight.json")))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("must not have SUPER");
    }

    @Test
    void rejectsMissingTriggerPrivilege() throws Exception {
        JdbcTemplate jdbc = eligibleJdbc();
        when(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.SCHEMA_PRIVILEGES WHERE GRANTEE=? AND TABLE_SCHEMA=DATABASE() AND PRIVILEGE_TYPE='TRIGGER'",
            Integer.class, grantee())).thenReturn(0);

        assertThatThrownBy(() -> E2aMysqlTriggerPreflight.check(jdbc, directory.resolve("preflight.json")))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("TRIGGER privilege");
    }

    @Test
    void rejectsUnexpectedPrincipal() throws Exception {
        JdbcTemplate jdbc = eligibleJdbc();
        when(jdbc.queryForObject("SELECT CURRENT_USER()", String.class)).thenReturn("unexpected@127.0.0.1");

        assertThatThrownBy(() -> E2aMysqlTriggerPreflight.check(jdbc, directory.resolve("preflight.json")))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("unexpected schema or principal");
    }

    @Test
    void preservesPriorEvidenceInsteadOfReplacingIt() throws Exception {
        JdbcTemplate jdbc = eligibleJdbc();
        Path evidence = directory.resolve("preflight.json");
        Files.writeString(evidence, "previous attempt");

        assertThatThrownBy(() -> E2aMysqlTriggerPreflight.check(jdbc, evidence))
            .isInstanceOf(FileAlreadyExistsException.class);

        assertThat(Files.readString(evidence)).isEqualTo("previous attempt");
    }

    @Test
    void acceptsTriggerPreconditionsWithoutChangingServerSettings() throws Exception {
        JdbcTemplate jdbc = eligibleJdbc();
        Path evidence = directory.resolve("preflight.json");

        E2aMysqlTriggerPreflight.check(jdbc, evidence);

        assertThat(Files.readString(evidence)).contains("\"result\":\"preconditions-met\"", "\"superGrants\":0");
    }

    private static JdbcTemplate eligibleJdbc() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT DATABASE()", String.class)).thenReturn(E2aMigrationEnvironment.DATABASE);
        when(jdbc.queryForObject("SELECT CURRENT_USER()", String.class)).thenReturn(E2aMigrationEnvironment.USER + "@127.0.0.1");
        when(jdbc.queryForObject("SELECT @@VERSION", String.class)).thenReturn("8.0.0");
        when(jdbc.queryForObject("SELECT @@GLOBAL.log_bin", Integer.class)).thenReturn(1);
        when(jdbc.queryForObject("SELECT @@GLOBAL.log_bin_trust_function_creators", Integer.class)).thenReturn(1);
        when(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.SCHEMA_PRIVILEGES WHERE GRANTEE=? AND TABLE_SCHEMA=DATABASE() AND PRIVILEGE_TYPE='TRIGGER'",
            Integer.class, grantee())).thenReturn(1);
        when(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.USER_PRIVILEGES WHERE GRANTEE=? AND PRIVILEGE_TYPE='SUPER'",
            Integer.class, grantee())).thenReturn(0);
        return jdbc;
    }

    private static String grantee() { return "'" + E2aMigrationEnvironment.USER + "'@'127.0.0.1'"; }
}
