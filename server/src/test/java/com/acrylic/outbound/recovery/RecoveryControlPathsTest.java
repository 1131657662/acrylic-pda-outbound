package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RecoveryControlPathsTest {
    @Test void configuredRestoreSetCannotOverlapInEitherDirection() throws Exception {
        var f = RecoveryControlFixture.create();
        for (Path other : List.of(f.root, f.root.getParent(), f.root.resolve("nested"))) {
            var reader = new RecoveryControlReader(new RecoveryControlLocation(f.root, f.installation, List.of(other)));
            assertThatThrownBy(() -> reader.read(f.checkpoint)).hasMessage("CONTROL_INSIDE_RESTORE_SET");
        }
    }

    @Test void hardlinkedAuthorityAndWitnessAreRejected() throws Exception {
        for (boolean witness : List.of(false, true)) {
            var f = RecoveryControlFixture.create();
            Path original = witness ? f.checkpoint : f.root.resolve("current.json");
            Files.createLink(f.root.getParent().resolve("alias"), original);
            assertThatThrownBy(f::read).hasMessage("CONTROL_FILE_ALIAS");
        }
    }

    @Test void redirectedHistoryIsRejectedWithoutTraversingIt() throws Exception {
        var f = RecoveryControlFixture.create(); Path original = f.root.getParent().resolve("original-history");
        Files.move(f.root.resolve("history"), original);
        if (RecoveryControlPaths.WINDOWS) {
            var process = new ProcessBuilder("cmd", "/c", "mklink", "/J", f.root.resolve("history").toString(), original.toString())
                .redirectErrorStream(true).start();
            assertThat(process.waitFor()).isZero();
        } else Files.createSymbolicLink(f.root.resolve("history"), original);
        assertThatThrownBy(f::read).isInstanceOf(RecoveryControlException.class);
        assertThat(original.resolve("000000000000000001.json")).exists();
    }

    @Test void dotDotAliasCannotBypassTheRestoreBoundary() throws Exception {
        var f = RecoveryControlFixture.create();
        var reader = new RecoveryControlReader(new RecoveryControlLocation(f.root.resolve("../authority"), f.installation, List.of(f.restoreRoot)));
        assertThatThrownBy(() -> reader.read(f.checkpoint)).hasMessage("CONTROL_PATH_ALIAS");
    }

    @Test void changedRestoreSetIsNotAcceptedByMatchingInstallationAlone() throws Exception {
        var f = RecoveryControlFixture.create();
        var reader = new RecoveryControlReader(new RecoveryControlLocation(f.root, f.installation, List.of(f.root.getParent().resolve("other"))));
        assertThatThrownBy(() -> reader.read(f.checkpoint)).hasMessage("CONTROL_RESTORE_SET_MISMATCH");
    }
}
