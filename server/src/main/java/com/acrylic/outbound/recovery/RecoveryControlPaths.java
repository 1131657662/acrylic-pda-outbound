package com.acrylic.outbound.recovery;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/** Fail closed on path redirection and unknown local storage semantics. */
final class RecoveryControlPaths {
    static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");
    private RecoveryControlPaths() { }

    static Path checked(Path path) throws IOException {
        require(path != null && path.isAbsolute() && !path.toString().startsWith("\\\\")
            && !path.toString().startsWith("//"), "CONTROL_ABSOLUTE_PATH_REQUIRED");
        for (Path part : path) {
            require(!part.toString().equals("..") && !part.toString().equals("."), "CONTROL_PATH_ALIAS");
            if (WINDOWS) windowsName(part.toString());
        }
        if (WINDOWS) RecoveryControlWindows.local(path);
        Path existing = null;
        for (Path item = path; item != null; item = item.getParent()) {
            if (!Files.exists(item, LinkOption.NOFOLLOW_LINKS)) continue;
            require(!Files.isSymbolicLink(item), "CONTROL_PATH_LINK");
            if (WINDOWS) RecoveryControlWindows.noReparse(item);
            if (existing == null) existing = item;
        }
        require(existing != null, "CONTROL_PARENT_MISSING");
        if (!WINDOWS) require(Set.of("ext4", "xfs", "btrfs", "tmpfs").contains(Files.getFileStore(existing).type()),
            "CONTROL_LOCAL_FILESYSTEM_UNVERIFIED");
        return Files.exists(path, LinkOption.NOFOLLOW_LINKS) ? path.toRealPath() : path.toAbsolutePath().normalize();
    }

    private static void windowsName(String name) {
        require(!name.endsWith(" ") && !name.endsWith(".") && !name.contains(":"), "CONTROL_PATH_ALIAS");
        String stem = name.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
        require(!stem.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]"), "CONTROL_PATH_ALIAS");
    }

    static Path file(Path path) throws IOException {
        Path checked = checked(path);
        require(Files.isRegularFile(checked, LinkOption.NOFOLLOW_LINKS), "CONTROL_FILE_MISSING");
        if (WINDOWS) RecoveryControlWindows.singleLink(checked);
        else require(((Number)Files.getAttribute(checked, "unix:nlink", LinkOption.NOFOLLOW_LINKS)).longValue() == 1,
            "CONTROL_FILE_ALIAS");
        return checked;
    }

    static boolean overlap(Path a, Path b) { return a.startsWith(b) || b.startsWith(a); }
}
