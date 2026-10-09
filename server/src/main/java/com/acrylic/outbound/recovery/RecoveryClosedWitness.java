package com.acrylic.outbound.recovery;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Separately pinned append-only digest witness outside transactions/ and every restore root. */
final class RecoveryClosedWitness {
    static final int DIGEST_BYTES = 32, MAX_RECORDS = 200000;
    private final Path file;
    private final String fileIdentity, headerHash;

    RecoveryClosedWitness(Path root, String fileIdentity, String headerHash) {
        file = root.resolve("transaction-witness.bin");
        this.fileIdentity = fileIdentity; this.headerHash = headerHash;
    }

    byte[] read() throws IOException {
        Path checked = RecoveryControlPaths.file(file);
        require(fileIdentity.equals(RecoveryWriterProtocol.fileIdentity(checked)), "CONTROL_CLOSED_WITNESS_IDENTITY");
        try (var input = Files.newInputStream(checked, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes((MAX_RECORDS + 1) * DIGEST_BYTES + 1);
            require(bytes.length >= DIGEST_BYTES && bytes.length <= (MAX_RECORDS + 1) * DIGEST_BYTES
                && bytes.length % DIGEST_BYTES == 0, "CONTROL_CLOSED_TRANSACTION_JOURNAL");
            require(headerHash.equals(sha256(Arrays.copyOf(bytes, DIGEST_BYTES))), "CONTROL_CLOSED_WITNESS_IDENTITY");
            return bytes;
        }
    }

    static int count(byte[] bytes) { return bytes.length / DIGEST_BYTES - 1; }

    static void matches(byte[] witness, int index, String hash) {
        byte[] expected = HexFormat.of().parseHex(hash);
        require(Arrays.equals(expected, Arrays.copyOfRange(witness, (index + 1) * DIGEST_BYTES,
            (index + 2) * DIGEST_BYTES)), "CONTROL_CLOSED_TRANSACTION_JOURNAL");
    }

    void append(int expectedRecords, String hash) throws IOException {
        byte[] before = read();
        require(count(before) == expectedRecords && expectedRecords < MAX_RECORDS, "CONTROL_CLOSED_TRANSACTION_JOURNAL");
        try (var channel = FileChannel.open(file, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            require(channel.size() == before.length, "CONTROL_CLOSED_TRANSACTION_JOURNAL");
            channel.position(before.length);
            var buffer = ByteBuffer.wrap(HexFormat.of().parseHex(hash));
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }
}
