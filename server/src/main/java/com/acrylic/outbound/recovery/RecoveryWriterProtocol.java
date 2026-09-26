package com.acrylic.outbound.recovery;

import java.io.IOException;
import java.nio.file.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Metadata reads never open writers.lock: closing that inode would drop POSIX locks. */
final class RecoveryWriterProtocol {
    private RecoveryWriterProtocol() { }

    static boolean validate(Path root, boolean required) throws IOException {
        Path marker = root.resolve("writers.json"), lock = root.resolve("writers.lock");
        if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS) && !Files.exists(lock, LinkOption.NOFOLLOW_LINKS)) {
            require(!required, "CONTROL_WRITER_PROTOCOL_REQUIRED"); return false;
        }
        var value = RecoveryControlHistory.read(marker);
        fields(value, "formatVersion installationId identitySha256 fileIdentity");
        require(value.path("formatVersion").isIntegralNumber() && value.path("formatVersion").canConvertToInt()
            && (value.path("formatVersion").asInt() == 1
            || value.path("formatVersion").asInt() == 2), "CONTROL_FORMAT_UNSUPPORTED");
        require(!required || value.path("formatVersion").asInt() == 2, "CONTROL_ADMISSION_PROTOCOL_REQUIRED");
        var identity = RecoveryControlHistory.read(root.resolve("identity.json"));
        require(uuid(value, "installationId").equals(uuid(identity, "installationId"))
            && hash(identity).equals(text(value, "identitySha256")), "CONTROL_WRITER_PROTOCOL_MISMATCH");
        require(Files.size(RecoveryControlPaths.file(lock)) == 1, "CONTROL_WRITER_LOCK_INVALID");
        require(fileIdentity(lock).equals(text(value, "fileIdentity")), "CONTROL_WRITER_LOCK_REPLACED");
        return true;
    }

    static String fileIdentity(Path path) throws IOException {
        if (RecoveryControlPaths.WINDOWS) return RecoveryControlWindows.fileIdentity(path);
        long device = ((Number)Files.getAttribute(path, "unix:dev", LinkOption.NOFOLLOW_LINKS)).longValue();
        long inode = ((Number)Files.getAttribute(path, "unix:ino", LinkOption.NOFOLLOW_LINKS)).longValue();
        return "posix:" + Long.toUnsignedString(device) + ":" + Long.toUnsignedString(inode);
    }
}
