package com.acrylic.outbound.recovery;

import java.nio.file.Path;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/** Explicit offline restoration of retained TEMP preparation bytes. Never issues credentials or opens normal admission. */
public final class RecoveryTemporaryPreparationRestore {
    private final RecoveryClosedAuthority authority;
    private final DataSource database;
    private final RecoveryReceiptKeys keys;

    public RecoveryTemporaryPreparationRestore(DataSource database, RecoveryClosedAuthority authority, RecoveryReceiptKeys keys) {
        this.database = Objects.requireNonNull(database); this.authority = Objects.requireNonNull(authority);
        this.keys = Objects.requireNonNull(keys);
    }

    public Result restore(Request request) {
        Objects.requireNonNull(request);
        authority.read();
        var original = read(request);
        var transaction = new RecoveryClosedTransaction(database, authority);
        var rows = new RecoveryTemporaryRestoreRows(new JdbcTemplate(database));
        return transaction.execute(original.source(), status -> {
            unchanged(request, original);
            var material = RecoveryTemporaryRestoreMaterial.read(original.source(), original.proof(), keys);
            new RecoveryTemporaryRestoreAuthorization(new JdbcTemplate(database)).requireMatches(material);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) {
                    unchanged(request, original);
                    new RecoveryTemporaryRestoreAuthorization(new JdbcTemplate(database)).requireMatches(material);
                    rows.requireComplete(material);
                }
            });
            return rows.restore(material);
        });
    }

    private Original read(Request request) {
        var source = new RecoverySourceReader(authority.location(), authority.checkpoint())
            .read(request.materialRoot(), request.sourcePinSha256(), authority.sourceIdentity());
        require(source.manifestSha256().equals(authority.sourcePinSha256()), "CONTROL_CLOSED_SOURCE_MISMATCH");
        var proof = new RecoveryOriginalTemporaryPreparation(authority.location(), authority.checkpoint(), keys)
            .read(request.original(), source);
        return new Original(source, proof);
    }

    private void unchanged(Request request, Original expected) {
        var actual = read(request);
        require(expected.proof().equals(actual.proof()) && expected.source().control().equals(actual.source().control()),
            "RECOVERY_TEMP_RESTORE_SOURCE_CHANGED");
        for (String kind : RecoverySourceManifest.OBJECTS)
            require(Arrays.equals(expected.source().original(kind), actual.source().original(kind)), "RECOVERY_TEMP_RESTORE_SOURCE_CHANGED");
    }

    public record Request(Path materialRoot, String sourcePinSha256, RecoveryOriginalTemporaryPreparation.Request original) {
        public Request { Objects.requireNonNull(materialRoot); Objects.requireNonNull(original); }
    }
    public record Result(int insertedParts, boolean insertedAck) {
        public boolean ordinaryWritesAllowed() { return false; }
        public boolean localDeletionAllowed() { return false; }
    }
    private record Original(RecoveryPinnedSource source, RecoveryOriginalTemporaryPreparation.Proof proof) { }
}
