package com.acrylic.outbound.recovery;

import java.nio.file.Path;
import java.util.List;

/**
 * Out-of-DB control authority for the application-entry checks. The originals come from the
 * independent Python writer, so the Java entry is a separate consumer of the same input.
 */
public final class RecoveryAdmissionEntryFixture {
    private final RecoveryTransactionFixture control;
    private final RecoveryControlLocation location;
    private final Path checkpoint;

    public RecoveryAdmissionEntryFixture(Path root) throws Exception {
        control = new RecoveryTransactionFixture(root);
        location = control.location;
        checkpoint = control.checkpoint;
    }

    /** Full opt-in options for the production entry wiring. */
    public String[] options() {
        var all = new java.util.ArrayList<String>(List.of(identityOptions()));
        all.add(0, "--outbound.recovery.admission.enabled=true");
        return all.toArray(String[]::new);
    }

    /** Control identity only; the fault-injected test context supplies its own datasource beans. */
    public String[] identityOptions() {
        return new String[] {
            "--outbound.recovery.root=" + location.root(),
            "--outbound.recovery.installation-id=" + location.installation(),
            "--outbound.recovery.restore-roots=" + String.join(",", location.restoreRoots().stream()
                .map(Path::toString).toList()),
            "--outbound.recovery.checkpoint=" + checkpoint
        };
    }

    RecoveryAdmittedDataSource admitted(javax.sql.DataSource source) {
        return new RecoveryAdmittedDataSource(source, location, checkpoint);
    }

    /** The independent writer must not be able to advance while the entry holds the lease. */
    void assertIndependentWriterBlocked() throws Exception { control.assertBlocked(); }

    /** Independent control advance after the scheduler has drained; old instances must refuse. */
    void advanceWorkerAuthority() throws Exception { control.assertAdvanced(); }
}
