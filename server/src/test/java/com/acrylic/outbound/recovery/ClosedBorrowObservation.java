package com.acrylic.outbound.recovery;

import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.assertj.core.api.Assertions.*;

/** The gate observes the very connection whose ownership the adapter has taken. */
final class ClosedBorrowObservation implements RecoveryRequestAdmittedDataSource.ConnectionAdmission {
    private final RecoveryClosedConnectionAdmission closed;
    private final List<Connection> physicals = new ArrayList<>();
    int calls;

    ClosedBorrowObservation(RecoveryControlFixture fixture) {
        closed = new RecoveryClosedConnectionAdmission(fixture.location(), fixture.checkpoint, 1);
    }

    @Override public void verify(Connection physical) throws SQLException {
        assertThat(physicals).doesNotContain(physical);
        physicals.add(physical);
        calls++;
        var jdbc = new JdbcTemplate(new SingleConnectionDataSource(physical, true));
        var before = counters(jdbc);
        var binlog = jdbc.queryForList("SHOW MASTER STATUS");
        try {
            closed.verify(physical);
        } finally {
            var after = counters(jdbc);
            assertThat(after.get("Com_select") - before.get("Com_select")).isEqualTo(1);
            for (String command : List.of("Com_insert", "Com_update", "Com_delete", "Com_replace"))
                assertThat(after.get(command)).as(command).isEqualTo(before.get(command));
            assertThat(jdbc.queryForList("SHOW MASTER STATUS")).isEqualTo(binlog);
            assertThat(physical.isClosed()).isFalse();
            System.out.println("REAL|adapter-check|call=" + calls + "|selectDelta=1|DML=0|binlogUnchanged");
        }
    }

    void allClosed() throws SQLException {
        for (Connection physical : physicals) assertThat(physical.isClosed()).isTrue();
    }

    private static Map<String, Long> counters(JdbcTemplate jdbc) {
        return jdbc.query("SHOW SESSION STATUS WHERE Variable_name IN ('Com_select','Com_insert','Com_update','Com_delete','Com_replace')",
            rs -> {
                var result = new TreeMap<String, Long>();
                while (rs.next()) result.put(rs.getString(1), rs.getLong(2));
                return result;
            });
    }
}
