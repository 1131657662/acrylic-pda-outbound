package com.acrylic.outbound.recovery;

import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.AbstractDataSource;
import static org.assertj.core.api.Assertions.*;

/** Drops the acknowledgement only after the real server commits; preserves the UNKNOWN marker. */
final class C1LostCommitCheck {
    private C1LostCommitCheck() { }

    static void verify(C1MainProfile profile) throws Exception {
        Path root = Files.createDirectory(profile.attempt.resolve("unknown-initial-authority"));
        Files.write(root.resolve("coordinator.lock"), new byte[]{'0'});
        Path trusted = profile.attempt.resolve("trusted-original.json");
        var store = new InitialNormalStore(new RecoveryControlLocation(root, InitialNormalFixture.INSTALLATION,
            List.of(profile.attempt.resolve("data"), profile.attempt.resolve("storage"))), trusted);
        store.prepare(Files.readAllBytes(trusted));
        var source = loseCommit(profile.jdbc.getDataSource());
        assertThatThrownBy(() -> RecoveryNormalRegistration.registerInitial(source, store,
            profile.attempt.resolve("no-closed-history"))).hasStackTraceContaining("test lost commit acknowledgement");
        assertThat(profile.jdbc.queryForObject("SELECT COUNT(*) FROM recovery_initial_normal", Integer.class)).isEqualTo(1);
        assertThat(root.resolve(InitialNormalStore.UNKNOWN)).exists();
        assertThat(root.resolve(InitialNormalStore.COMPLETE)).doesNotExist();
        assertThatThrownBy(() -> RecoveryNormalRegistration.registerInitial(profile.jdbc.getDataSource(), store,
            profile.attempt.resolve("no-closed-history"))).hasMessage("INITIAL_NORMAL_UNKNOWN");
        assertThat(RecoveryAdmissionVerifier.verifyInitial(new RecoveryAdmissionQueries(profile.jdbc.getDataSource()),
            store, profile.attempt.resolve("no-closed-history")).admitted()).isFalse();
        Files.writeString(profile.attempt.resolve("c1-lost-commit.txt"), "Real commit returned then acknowledgement fault; "
            + "DB original row retained; external completion absent; durable UNKNOWN denies fresh coordinator and verifier.\n");
    }

    private static DataSource loseCommit(DataSource real) {
        return new AbstractDataSource() {
            public Connection getConnection() throws SQLException { return wrap(real.getConnection()); }
            public Connection getConnection(String user, String password) throws SQLException { return wrap(real.getConnection(user, password)); }
        };
    }

    private static Connection wrap(Connection real) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
            try {
                Object result = method.invoke(real, args);
                if (method.getName().equals("commit")) throw new SQLException("test lost commit acknowledgement", "08007");
                return result;
            } catch (InvocationTargetException error) { throw error.getCause(); }
        });
    }
}
