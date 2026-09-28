package com.acrylic.outbound.recovery;

import com.sun.jna.Native;
import com.sun.jna.WString;
import com.sun.jna.win32.StdCallLibrary;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Arrays;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/** Immutable publication under the coordinator lock. Incomplete/conflicting files are retained. */
final class InitialNormalDisk {
    private InitialNormalDisk() { }
    interface MoveApi extends StdCallLibrary {
        MoveApi INSTANCE = Native.load("kernel32", MoveApi.class);
        boolean MoveFileExW(WString source, WString target, int flags);
    }

    static byte[] read(Path path) throws IOException {
        try (var stream = Files.newInputStream(RecoveryControlPaths.file(path), LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = stream.readNBytes(RecoveryControlJson.MAX_BYTES + 1);
            require(bytes.length <= RecoveryControlJson.MAX_BYTES, "INITIAL_NORMAL_FILE_LIMIT");
            return bytes;
        }
    }

    static void publish(Path root, String name, byte[] bytes) throws IOException {
        RecoveryControlLock.requireOwner(root);
        Path target = root.resolve(name), pending = root.resolve(name + ".pending");
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            require(!Files.exists(pending, LinkOption.NOFOLLOW_LINKS), "INITIAL_NORMAL_PENDING_CONFLICT");
            same(target, bytes); force(target); syncDirectory(root); return;
        }
        if (Files.exists(pending, LinkOption.NOFOLLOW_LINKS)) same(pending, bytes);
        else writeNew(pending, bytes);
        force(pending);
        move(pending, target);
        syncDirectory(root);
        same(target, bytes);
    }

    private static void same(Path path, byte[] bytes) throws IOException {
        require(Arrays.equals(read(path), bytes), "INITIAL_NORMAL_FILE_CONFLICT");
    }

    private static void writeNew(Path path, byte[] bytes) throws IOException {
        RecoveryControlPaths.checked(path);
        try (var file = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS)) {
            var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) file.write(buffer);
            file.force(true);
        }
    }

    private static void force(Path path) throws IOException {
        try (var file = FileChannel.open(RecoveryControlPaths.file(path), StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS)) { file.force(true); }
    }

    private static void move(Path pending, Path target) throws IOException {
        if (RecoveryControlPaths.WINDOWS) {
            // WRITE_THROUGH, deliberately without REPLACE_EXISTING.
            require(MoveApi.INSTANCE.MoveFileExW(new WString(pending.toString()), new WString(target.toString()), 8),
                "INITIAL_NORMAL_PUBLISH_FAILED");
        } else Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void syncDirectory(Path root) throws IOException {
        // Windows publication uses MoveFileExW WRITE_THROUGH, as the existing control-disk protocol does.
        if (RecoveryControlPaths.WINDOWS) return;
        try (var directory = FileChannel.open(root, StandardOpenOption.READ)) { directory.force(true); }
    }
}
