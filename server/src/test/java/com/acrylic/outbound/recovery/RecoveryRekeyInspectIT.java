package com.acrylic.outbound.recovery;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.*;
import static org.assertj.core.api.Assertions.*;

class RecoveryRekeyInspectIT {
    @TempDir(cleanup = CleanupMode.NEVER) Path parent;

    @Test void inspectsActualCompletedRotationWithoutAuthorizingRecovery() throws Exception {
        var f = RecoveryRekeyInspectFixture.rotated(parent);
        assertThat(f.inspect("new", 0)).isEqualTo("ALL_NEW");
    }

    @Test void originalTreeRemainsStoppedEvenWhenEveryRecordIsOld() throws Exception {
        var f = RecoveryRekeyInspectFixture.rotated(parent);
        for (String file : new String[]{"lease-root.json", "leases/index.json", "processes/" + f.processFiles().getFirst().getFileName()})
            f.original(file);
        assertThat(f.inspect("old", 0)).isEqualTo("ALL_OLD");
    }

    @Test void recognizesMixedSignaturesWithoutRewritingAnyOriginal() throws Exception {
        var f = RecoveryRekeyInspectFixture.rotated(parent);
        f.original("processes/" + f.processFiles().getFirst().getFileName());
        assertThat(f.inspect("mixed", 0)).isEqualTo("MIXED");
    }

    @Test void retainsRecognizedPendingFileAndRequiresStop() throws Exception {
        var f = RecoveryRekeyInspectFixture.rotated(parent); Path root = f.rotation().supply().roots().root();
        Files.copy(root.resolve("lease-root.json"), root.resolve("lease-root.json.rekey.pending"));
        assertThat(f.inspect("pending", 0)).isEqualTo("PENDING");
    }

    @Test void corruptPendingFileMakesDiagnosisUnknown() throws Exception {
        var f = RecoveryRekeyInspectFixture.rotated(parent);
        Files.writeString(f.rotation().supply().roots().root().resolve("lease-root.json.rekey.pending"), "corrupt");
        assertThat(f.inspect("pending-corrupt", 3)).isEqualTo("UNKNOWN");
    }

    @Test void missingBackupRecordCannotBeHiddenByValidRemainingSignatures() throws Exception {
        var f = RecoveryRekeyInspectFixture.rotated(parent); Path process = f.processFiles().getFirst();
        Files.move(process, f.rotation().supply().base().resolve("retained-missing-original.json"));
        assertThat(f.inspect("missing", 3)).isEqualTo("UNKNOWN");
    }

    @Test void tamperedCurrentRecordIsNeverClassifiedAsOldOrNew() throws Exception {
        var f = RecoveryRekeyInspectFixture.rotated(parent);
        Path target = f.rotation().supply().roots().root().resolve("leases/index.json");
        Files.copy(target, f.rotation().supply().base().resolve("retained-original-index.json"));
        Files.writeString(target, "{}\n");
        assertThat(f.inspect("tampered", 3)).isEqualTo("UNKNOWN");
    }
}
