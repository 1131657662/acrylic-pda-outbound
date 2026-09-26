package com.acrylic.outbound.recovery;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/** Explicit installation and restore-set identity; no automatic bootstrap. */
public record RecoveryControlLocation(Path root, UUID installation, List<Path> restoreRoots) {
    public RecoveryControlLocation {
        require(installation != null && !installation.equals(new UUID(0, 0)), "CONTROL_UUID");
        require(restoreRoots != null && !restoreRoots.isEmpty(), "CONTROL_RESTORE_SET_REQUIRED");
        restoreRoots = List.copyOf(restoreRoots);
    }

    RecoveryControlLocation checked() throws IOException {
        Path authority = RecoveryControlPaths.checked(root);
        var roots = new TreeSet<Path>((a, b) -> RecoveryControlJson.compareText(a.toString(), b.toString()));
        for (Path item : restoreRoots) {
            Path other = RecoveryControlPaths.checked(item);
            require(!RecoveryControlPaths.overlap(authority, other), "CONTROL_INSIDE_RESTORE_SET");
            roots.add(other);
        }
        return new RecoveryControlLocation(authority, installation, List.copyOf(roots));
    }

    Path checkpoint(Path path) throws IOException {
        Path trusted = RecoveryControlPaths.file(path);
        require(!RecoveryControlPaths.overlap(root, trusted), "CONTROL_CHECKPOINT_INSIDE_AUTHORITY");
        for (Path restored : restoreRoots) require(!RecoveryControlPaths.overlap(restored, trusted),
            "CONTROL_CHECKPOINT_INSIDE_RESTORE_SET");
        return trusted;
    }
}
