package com.acrylic.outbound.recovery;

import com.sun.jna.*;
import com.sun.jna.win32.StdCallLibrary;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/** Durable immutable journal publication; any interrupted publication remains a refusal input. */
final class RecoveryClosedDisk {
    interface MoveApi extends StdCallLibrary {
        MoveApi INSTANCE = Native.load("kernel32", MoveApi.class);
        boolean MoveFileExW(WString source, WString target, int flags);
    }
    private RecoveryClosedDisk() { }

    static void write(Path target, byte[] bytes) throws IOException {
        RecoveryControlPaths.checked(target);
        require(!Files.exists(target, LinkOption.NOFOLLOW_LINKS), "CONTROL_CLOSED_TRANSACTION_JOURNAL");
        Path pending = target.resolveSibling(target.getFileName() + ".pending");
        try (var file = FileChannel.open(pending, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS)) {
            var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) file.write(buffer);
            file.force(true);
        }
        if (RecoveryControlPaths.WINDOWS)
            require(MoveApi.INSTANCE.MoveFileExW(new WString(pending.toString()), new WString(target.toString()), 8),
                "CONTROL_CLOSED_PUBLISH_FAILED");
        else {
            Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE);
            try (var directory = FileChannel.open(target.getParent(), StandardOpenOption.READ)) { directory.force(true); }
        }
    }
}
