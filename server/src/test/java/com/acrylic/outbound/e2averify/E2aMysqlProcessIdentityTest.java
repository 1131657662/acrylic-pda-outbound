package com.acrylic.outbound.e2averify;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class E2aMysqlProcessIdentityTest {
    private static final Path DATA = E2aMigrationRunner.workspace().resolve(E2aMigrationEnvironment.DATA_DIR);
    private static final String EXECUTABLE = E2aMysqlLauncher.MYSQLD.toString();
    private static final String COMMAND = '"' + EXECUTABLE + '"' + " --no-defaults --datadir=" + DATA
        + " --port=19329 --bind-address=127.0.0.1 --mysqlx=OFF --skip-name-resolve"
        + " --skip-networking=false --skip-grant-tables=false --log-bin-trust-function-creators=ON --init-file="
        + E2aMigrationRunner.workspace().resolve(E2aMigrationEnvironment.PRIVATE_DIR)
            .resolve("bootstrap-account.sql").toAbsolutePath().normalize();

    @Test
    void acceptsExactDirectChild() {
        var listener = new E2aMysqlProcessIdentity.Listener(23260, 9872, EXECUTABLE, COMMAND);

        assertThatCode(() -> E2aMysqlProcessIdentity.requireExpected(listener, 9872, DATA)).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnrelatedListener() {
        var listener = new E2aMysqlProcessIdentity.Listener(23260, 9999, EXECUTABLE, COMMAND);

        assertThatThrownBy(() -> E2aMysqlProcessIdentity.requireExpected(listener, 9872, DATA))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsAnotherDataDirectory() {
        var listener = new E2aMysqlProcessIdentity.Listener(23260, 9872, EXECUTABLE,
            COMMAND.replace("mysql19329-attempt8", "mysql19329-attempt7"));

        assertThatThrownBy(() -> E2aMysqlProcessIdentity.requireExpected(listener, 9872, DATA))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsOtherExecutable() {
        var listener = new E2aMysqlProcessIdentity.Listener(23260, 9872, "C:/other/mysqld.exe", COMMAND);

        assertThatThrownBy(() -> E2aMysqlProcessIdentity.requireExpected(listener, 9872, DATA))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsOldInitializationFile() {
        var listener = new E2aMysqlProcessIdentity.Listener(23260, 9872, EXECUTABLE,
            COMMAND.replace("launcher-private-attempt8", "launcher-private-attempt7"));

        assertThatThrownBy(() -> E2aMysqlProcessIdentity.requireExpected(listener, 9872, DATA))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsUnboundListener() {
        var listener = new E2aMysqlProcessIdentity.Listener(23260, 9872, EXECUTABLE,
            COMMAND.replace("--bind-address=127.0.0.1", "--bind-address=0.0.0.0"));

        assertThatThrownBy(() -> E2aMysqlProcessIdentity.requireExpected(listener, 9872, DATA))
            .isInstanceOf(IllegalStateException.class);
    }
}
