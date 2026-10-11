package com.acrylic.outbound.recovery;

import java.io.IOException;
import java.nio.file.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** A server-configured local file and independent content pin; never supplied by the HTTP caller. */
public record RecoveryRecipientDownloadMaterial(Path file,String independentSha256) {
    public RecoveryRecipientDownloadMaterial {
        require(file!=null && independentSha256!=null && independentSha256.matches("[a-f0-9]{64}")
            && !ZERO_HASH.equals(independentSha256),"RECOVERY_DOWNLOAD_INPUT_PIN_REQUIRED");
    }
    byte[] read(int limit) {
        try(var input=Files.newInputStream(RecoveryControlPaths.file(file),LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes=input.readNBytes(limit+1);
            require(bytes.length>0 && bytes.length<=limit,"RECOVERY_DOWNLOAD_INPUT_SIZE");
            require(sha256(bytes).equals(independentSha256),"RECOVERY_DOWNLOAD_INPUT_PIN_MISMATCH"); return bytes;
        } catch(IOException unavailable) { throw new RecoveryControlException("RECOVERY_DOWNLOAD_INPUT_UNAVAILABLE",unavailable); }
    }
    void outside(RecoveryClosedAuthority authority) {
        try {
            Path path=RecoveryControlPaths.file(file);
            for(Path root:authority.location().restoreRoots())
                require(!RecoveryControlPaths.overlap(path,root),"RECOVERY_DOWNLOAD_INPUT_INSIDE_RESTORE");
            for(Path root:java.util.List.of(authority.location().root(),authority.authorityRoot(),authority.checkpoint()))
                require(!RecoveryControlPaths.overlap(path,root),"RECOVERY_DOWNLOAD_INPUT_AUTHORITY_OVERLAP");
        } catch(IOException unavailable) { throw new RecoveryControlException("RECOVERY_DOWNLOAD_INPUT_UNAVAILABLE",unavailable); }
    }
}
