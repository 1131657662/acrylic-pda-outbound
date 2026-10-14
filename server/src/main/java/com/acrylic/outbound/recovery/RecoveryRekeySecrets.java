package com.acrylic.outbound.recovery;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.security.MessageDigest;
import java.util.Arrays;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Reads a closed binary pipe and the external retained key using read-only handles. */
final class RecoveryRekeySecrets implements AutoCloseable {
    final byte[] oldAnchor;
    final byte[] newAnchor;

    private RecoveryRekeySecrets(byte[] supplied) {
        oldAnchor = Arrays.copyOfRange(supplied, 0, 32);
        newAnchor = Arrays.copyOfRange(supplied, 32, 64);
    }

    static RecoveryRekeySecrets read(InputStream input, RecoveryRekeyRequest request) throws IOException {
        byte[] supplied = input.readNBytes(65);
        try {
            require(supplied.length == 64, "RECOVERY_REKEY_SECRET_INPUT");
            var secrets = new RecoveryRekeySecrets(supplied);
            try {
                require(sha256(secrets.oldAnchor).equals(request.pins().oldAnchorId()), "RECOVERY_REKEY_OLD_ANCHOR");
                require(sha256(secrets.newAnchor).equals(request.pins().newAnchorId()), "RECOVERY_REKEY_NEW_ANCHOR");
                verifyRetained(request, secrets.oldAnchor);
                return secrets;
            } catch (IOException | RuntimeException failure) { secrets.close(); throw failure; }
        } finally { Arrays.fill(supplied, (byte) 0); }
    }

    private static void verifyRetained(RecoveryRekeyRequest request, byte[] anchor) throws IOException {
        try (var stream = Files.newInputStream(request.retainedAnchorFile(), LinkOption.NOFOLLOW_LINKS)) {
            byte[] retained = stream.readNBytes(33);
            try {
                require(retained.length == 32 && MessageDigest.isEqual(retained, anchor), "RECOVERY_REKEY_HISTORY_UNAVAILABLE");
            } finally { Arrays.fill(retained, (byte) 0); }
        }
    }

    @Override public void close() {
        Arrays.fill(oldAnchor, (byte) 0); Arrays.fill(newAnchor, (byte) 0);
    }
}
