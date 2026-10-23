package com.acrylic.outbound.e2averify;

import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Dedicated fixture endpoint; never accepts arbitrary database URLs or system MySQL. */
final class E2aArchiveRestoreTarget {
    final Path attempt;
    final String database, user, url, password;
    final int port;

    E2aArchiveRestoreTarget(E2aCrossSourceProfile profile) { this(profile, false); }

    private E2aArchiveRestoreTarget(E2aCrossSourceProfile profile, boolean cross) {
        String run = System.getenv(cross ? "E2A_COPY_RUN" : "E2A_CROSS_RUN");
        assertThat(run).matches("[1-9][0-9]{0,3}");
        attempt = cross ? E2aCrossSourceProfile.ROOT.resolve(".local/e2a-cross-20260925-" + run) : profile.attempt;
        database = (cross ? "acrylic_e2a_cross_20260925_" + run : profile.database) + "_restore";
        user = cross ? "e2a_cross_20260925_" + run : profile.user;
        port = cross ? 22000 + Integer.parseInt(run) : E2aTestPorts.database();
        password = System.getenv(cross ? "E2A_COPY_PASSWORD" : "E2A_CROSS_PASSWORD");
        assertThat(password).matches("[0-9a-f]{64}");
        url = "jdbc:mysql://127.0.0.1:" + port + "/" + database
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        assertThat(real(attempt)).startsWith(real(E2aCrossSourceProfile.ROOT.resolve(".local")));
        assertThat(Files.isDirectory(attempt.resolve("data"))).isTrue();
        assertThat(Files.isRegularFile(attempt.resolve("private/client.cnf"))).isTrue();
        if (cross) {
            assertThat(System.getenv("E2A_COPY_PORT")).isEqualTo(Integer.toString(port));
            assertThat(real(attempt)).isNotEqualTo(real(profile.attempt));
            assertThat(port).isNotEqualTo(E2aTestPorts.database());
            assertThat(Files.exists(attempt.resolve("test-started.marker"))).isFalse();
        }
    }

    static E2aArchiveRestoreTarget crossInstance(E2aCrossSourceProfile profile) {
        assertThat(System.getenv("E2A_COPY_VERIFY")).isEqualTo("true");
        return new E2aArchiveRestoreTarget(profile, true);
    }

    void requireIdentity(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(database);
        assertThat(jdbc.queryForObject("SELECT CURRENT_USER()", String.class)).isEqualTo(user + "@127.0.0.1");
        assertThat(jdbc.queryForObject("SELECT @@port", Integer.class)).isEqualTo(port);
        assertThat(real(Path.of(jdbc.queryForObject("SELECT @@datadir", String.class))))
            .isEqualTo(real(attempt.resolve("data")));
    }

    private static Path real(Path path) {
        try { return path.toRealPath(); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
}
