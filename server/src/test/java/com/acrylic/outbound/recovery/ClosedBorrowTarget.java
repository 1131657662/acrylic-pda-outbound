package com.acrylic.outbound.recovery;

import java.nio.file.Path;
import java.sql.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

/** Explicit second-instance identity guard: never accepts system MySQL or an arbitrary URL. */
final class ClosedBorrowTarget {
    static DriverManagerDataSource source() {
        String url = System.getenv("OUTBOUND_COPY_DB_URL"), user = System.getenv("OUTBOUND_COPY_DB_USER");
        var match = java.util.regex.Pattern.compile("jdbc:mysql://127\\.0\\.0\\.1:(19[4-9][0-9]{2})/(p5_lost_[0-9]+)\\?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC")
            .matcher(url == null ? "" : url);
        assertThat(match.matches()).as("explicit isolated loopback target required").isTrue();
        assertThat(user).isEqualTo(match.group(2)).isEqualTo(EffectRealEnvironment.USER);
        assertThat(url).isNotEqualTo(EffectRealEnvironment.URL);
        return new DriverManagerDataSource(url, user, System.getenv("OUTBOUND_COPY_DB_PASSWORD"));
    }

    static void requireIdentity(Connection connection, Path output) throws Exception {
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT @@port,@@datadir,CURRENT_USER(),@@server_uuid,DATABASE()")) {
            assertThat(rows.next()).isTrue();
            int port = rows.getInt(1);
            assertThat(System.getenv("OUTBOUND_COPY_DB_URL")).startsWith("jdbc:mysql://127.0.0.1:" + port + "/");
            Path local = output.toRealPath();
            while (local != null && !local.getFileName().toString().equals(".local")) local = local.getParent();
            assertThat(local).isNotNull();
            Path datadir = Path.of(rows.getString(2).replace('\\', '/')).toRealPath();
            assertThat(datadir).startsWith(local.resolve("mysql" + port + "-r09batch"));
            assertThat(rows.getString(3)).isEqualTo(EffectRealEnvironment.USER + "@127.0.0.1");
            assertThat(rows.getString(5)).isEqualTo(EffectRealEnvironment.USER);
            EffectRealEnvironment.record("REAL|adapter-target|port=" + port + "|datadir=" + datadir
                + "|user=" + rows.getString(3) + "|uuid=" + rows.getString(4) + "|schema=" + rows.getString(5));
        }
    }
}
