package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.*;
import static org.assertj.core.api.Assertions.*;

/** An empty lease index must not hide retained process signatures or unfinished replacements. */
class RecoveryRekeyStartupIT {
    @TempDir(cleanup = CleanupMode.NEVER) Path parent;

    @Test void mixedProcessSignaturesRefuseStartupWithOriginalKeyAndMarker() throws Exception {
        var f = RecoveryRekeyInspectFixture.rotated(parent);
        f.original("lease-root.json"); f.original("leases/index.json");
        var before = f.facts(); var authority = f.rotation().supply().authority();
        try { assertThatThrownBy(authority::validateStartup).isInstanceOf(SQLException.class); }
        finally { authority.retire(); }
        assertThat(f.facts()).isEqualTo(before);
    }

    @Test void unfinishedMarkerPendingRefusesStartupWithoutRemovingEvidence() throws Exception {
        var f = RecoveryRekeyCliFixture.create(parent); Path root = f.supply().roots().root();
        Files.write(root.resolve("lease-root.json.rekey.pending"), new byte[0]);
        var before = f.facts(); var authority = f.supply().authority();
        try { assertThatThrownBy(authority::validateStartup).isInstanceOf(SQLException.class); }
        finally { authority.retire(); }
        assertThat(f.facts()).isEqualTo(before);
    }
}
