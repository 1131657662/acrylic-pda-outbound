package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Verifies the pre-restored private files against exact original E2A bytes; never writes or follows links. */
final class RecoveryTemporaryEvidenceFiles {
    private RecoveryTemporaryEvidenceFiles() { }

    static void verify(Path storage,JsonNode rows,RecoveryOriginalEvidence.Request request) {
        try {
            Path root=RecoveryControlPaths.checked(storage);
            var originals=new HashMap<>(request.bundle().originalObjects());
            var stop=RecoveryUnsubmittedEvidence.expected(request).stopProofBytes(); originals.put(sha256(stop),stop);
            for(byte[] command:request.bundle().pageBytes()) {
                byte[] page=SubmissionJson.canonical(SubmissionJson.parse(command).path("pageCore")).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                originals.put(sha256(page),page);
            }
            var seen=new HashSet<String>();
            for(var row:rows.path("source_evidence_object")) {
                String hash=text(row,"bytes_sha256"), key=text(row,"file_key");
                require(hash.matches("[a-f0-9]{64}") && originals.containsKey(hash),"RECOVERY_EVIDENCE_FILE_ORIGINAL");
                require(key.matches("[a-zA-Z0-9_./-]{1,160}") && !key.startsWith("/")
                    && Arrays.stream(key.split("/",-1)).noneMatch(part -> part.isEmpty() || part.equals(".") || part.equals("..")),
                    "RECOVERY_EVIDENCE_FILE_KEY");
                Path file=RecoveryControlPaths.file(root.resolve(key));
                require(file.startsWith(root) && !file.equals(root),"RECOVERY_EVIDENCE_FILE_KEY");
                byte[] expected=originals.get(hash);
                require(revision(row,"byte_count")==expected.length,"RECOVERY_EVIDENCE_FILE_SIZE");
                try(var input=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)) {
                    require(Arrays.equals(input.readNBytes(expected.length+1),expected),"RECOVERY_EVIDENCE_FILE_CHANGED");
                }
                seen.add(hash);
            }
            require(seen.equals(originals.keySet()),"RECOVERY_EVIDENCE_FILE_SET");
        } catch(IOException unavailable) { throw new RecoveryControlException("RECOVERY_EVIDENCE_FILE_UNAVAILABLE",unavailable); }
    }
}
