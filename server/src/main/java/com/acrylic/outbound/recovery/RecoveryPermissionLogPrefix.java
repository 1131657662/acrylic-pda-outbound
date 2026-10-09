package com.acrylic.outbound.recovery;

import java.nio.file.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Only the explicitly configured original local binlog, validated against the live server path, may supply a boundary prefix. */
final class RecoveryPermissionLogPrefix {
    private final Path root;
    RecoveryPermissionLogPrefix(Path root) { require(root != null, "RECOVERY_PERMISSION_LOG_ROOT"); this.root = root; }

    String capture(Request request) {
        try {
            Path checked = RecoveryControlPaths.checked(root), basename = Path.of(request.liveBasename()).toAbsolutePath().normalize();
            require(checked.equals(basename.getParent()) && request.file().matches("[A-Za-z0-9_-]{1,100}\\.[0-9]{6}")
                && request.file().startsWith(basename.getFileName().toString() + "."), "RECOVERY_PERMISSION_LOG_ROOT");
            require(request.position() > 4 && request.position() <= RecoveryBinlogFile.MAX_BYTES, "RECOVERY_PERMISSION_LOG_SIZE");
            Path file = RecoveryControlPaths.file(checked.resolve(request.file())); byte[] bytes;
            try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                bytes = input.readNBytes(Math.toIntExact(request.position()));
            }
            require(bytes.length == request.position(), "RECOVERY_PERMISSION_LOG_TRUNCATED");
            return sha256(bytes);
        } catch (java.io.IOException unavailable) { throw new RecoveryControlException("RECOVERY_PERMISSION_LOG_UNAVAILABLE", unavailable); }
    }
    record Request(String liveBasename, String file, long position) { }
}
