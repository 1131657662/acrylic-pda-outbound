package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.Arrays;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Rechecks the pre-restored payload and every native E2A file without writing files. */
final class RecoveryReceivedTemporaryFiles {
    private RecoveryReceivedTemporaryFiles() { }
    static void verify(Path storage, JsonNode rows, byte[] payload) {
        try {
            var root=RecoveryControlPaths.checked(storage); var head=rows.path("head");
            require(Arrays.equals(file(root,text(head,"file_key"),revision(head,"byte_count")),payload),"RECOVERY_RECEIVED_PAYLOAD_FILE");
            for(var row:rows.path("evidence").path("source_evidence_object")) {
                byte[] bytes=file(root,text(row,"file_key"),revision(row,"byte_count"));
                require(sha256(bytes).equals(text(row,"bytes_sha256")),"RECOVERY_RECEIVED_EVIDENCE_FILE");
            }
        } catch(java.io.IOException error) { throw new RecoveryControlException("RECOVERY_RECEIVED_FILE_UNAVAILABLE",error); }
    }
    private static byte[] file(Path root, String key, long size) throws java.io.IOException {
        require(size>0 && size<=33554432 && key.matches("[a-zA-Z0-9_./-]{1,160}") && !key.startsWith("/")
            && Arrays.stream(key.split("/",-1)).noneMatch(part -> part.isEmpty() || part.equals(".") || part.equals("..")),"RECOVERY_RECEIVED_FILE_KEY");
        Path file=RecoveryControlPaths.file(root.resolve(key)); require(file.startsWith(root) && !file.equals(root),"RECOVERY_RECEIVED_FILE_KEY");
        try(var input=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes=input.readNBytes(Math.toIntExact(size+1)); require(bytes.length==size,"RECOVERY_RECEIVED_FILE_SIZE"); return bytes;
        }
    }
}
