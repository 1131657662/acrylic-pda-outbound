package com.acrylic.outbound.recovery;

import java.util.List;
import java.util.UUID;

/**
 * Read-only view of both admission sides. Implementations only observe; they never initialize,
 * repair, register or grant anything.
 */
public interface RecoveryAdmissionEvidence {
    /** The database installation binding, or null when no closed-phase registration was projected. */
    Binding binding() throws Exception;

    /** The durable NORMAL admission record, or null when it was never registered. */
    Admission admission() throws Exception;

    /** The latest closed-phase projection row. */
    Projection projection() throws Exception;

    /** Current server_state as written by the admission projection. */
    State state() throws Exception;

    /** Missing installation binding: the closed-phase projection was never registered. */
    record Binding(UUID installation, long baseline) { }

    /** The frozen admission record: which authority revision and hash it was registered against. */
    record Admission(long authorityRevision, String recordSha256, String intentSha256) { }

    record Projection(long revision, String hash, String phase, long numeric) { }

    record State(String mode, long numeric) { }

    static List<String> none() { return List.of(); }
}
