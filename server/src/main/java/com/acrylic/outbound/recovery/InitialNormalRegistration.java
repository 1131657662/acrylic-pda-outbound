package com.acrylic.outbound.recovery;

import java.io.IOException;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Explicit offline coordinator; no bean, route, auto-bootstrap, recovery transition or capability. */
public final class InitialNormalRegistration {
    private final InitialNormalStore store;
    private final Path closed;
    private final JdbcTemplate jdbc;
    private final InitialNormalTransaction transaction;

    public InitialNormalRegistration(DataSource source, InitialNormalStore store, Path closedAuthority) {
        this.store = store;
        this.closed = closedAuthority;
        this.jdbc = new JdbcTemplate(source);
        this.transaction = new InitialNormalTransaction(source, store);
    }

    public Result register() {
        return store.locked(() -> {
            store.requireUnclosed(closed);
            var external = RecoveryNormalIntent.initial(store);
            boolean replay = transaction.execute(() -> registerLocked(external));
            InitialNormalVerification.match(external.request(), InitialNormalQueries.read(jdbc), true);
            store.requireUnclosed(closed);
            store.completeLocked(external.request().sha256());
            return new Result(replay ? "ALREADY_REGISTERED" : "REGISTERED", external.request().sha256(), false, false);
        });
    }

    private boolean registerLocked(InitialNormalStore.Snapshot external) {
        new RecoveryProjectionRows(jdbc).lockState();
        var database = InitialNormalQueries.read(jdbc);
        InitialNormalVerification.match(external.request(), database, external.committed());
        if (database.request() != null) return true;
        new RecoveryNormalRows(jdbc).initial(external.request());
        InitialNormalVerification.match(external.request(), InitialNormalQueries.read(jdbc), true);
        return false;
    }

    public record Result(String outcome, String requestSha256, boolean ordinaryWritesAllowed, boolean localDeletionAllowed) { }
}
