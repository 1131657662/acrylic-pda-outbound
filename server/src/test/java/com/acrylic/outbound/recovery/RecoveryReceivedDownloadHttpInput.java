package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.Base64;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Explicitly packages the already pinned retained inputs for the actual production loader. */
public final class RecoveryReceivedDownloadHttpInput {
    public static void save(Path attempt,Path original) throws Exception {
        var scope=pinned(attempt,"scope"); var q=pinned(attempt,"qualification"); var session=pinned(attempt,"session");
        var value=SubmissionJson.object().put("formatVersion",1).put("purpose","RECOVERED_TEMP_DOWNLOAD_INPUT")
            .put("caseId",readCanonical(Files.readAllBytes(Path.of(text(q,"qualificationFile")))).path("approval").path("caseId").asText());
        reference(value,"archive",attempt.resolve("physical-restart-input.json"));
        reference(value,"originalFault",attempt.resolve("recovered-fault/fault-command.json"));
        reference(value,"operationsIdentity",Path.of(text(q,"identityFile"))); reference(value,"operationsApproval",Path.of(text(scope,"approvalFile")));
        reference(value,"admission",Path.of(text(q,"admissionFile"))); reference(value,"qualification",Path.of(text(q,"qualificationFile")));
        reference(value,"session",Path.of(text(session,"sessionFile")));
        value.put("scopeRoot",text(scope,"storeRoot")).put("scopeLockIdentity",text(scope,"storeLockIdentity"));
        reference(value,"numberBegin",original.resolve("checkpoint-snapshot.json")); reference(value,"numberEnd",original.resolve("checkpoint-source-final.json"));
        value.putArray("receiptPublicKeys").add(Base64.getEncoder().encodeToString(Files.readAllBytes(attempt.resolve("private/server-public.der"))));
        byte[] bytes=RecoveryControlJson.encode(value); Files.write(attempt.resolve("download-http-input.json"),bytes);
        Files.writeString(attempt.resolve("download-http-input.sha256"),sha256(bytes));
    }
    public static String[] options(Path attempt) throws Exception {
        Path file=attempt.resolve("download-http-input.json");
        if(!Files.exists(file)) return new String[0];
        return new String[]{"--outbound.handover.recovered-download.enabled=true","--outbound.handover.recovered-download.input="+file,
            "--outbound.handover.recovered-download.input-sha256="+Files.readString(attempt.resolve("download-http-input.sha256"))};
    }
    private static com.fasterxml.jackson.databind.JsonNode pinned(Path attempt,String kind) throws Exception {
        byte[] bytes=Files.readAllBytes(attempt.resolve(kind+"-restart-input.json"));
        org.assertj.core.api.Assertions.assertThat(sha256(bytes)).isEqualTo(Files.readString(attempt.resolve(kind+"-restart-input.sha256")));
        return readCanonical(bytes);
    }
    private static void reference(ObjectNode value,String name,Path file) throws Exception {
        value.putObject(name).put("file",file.toString()).put("independentSha256",sha256(Files.readAllBytes(file)));
    }
    private RecoveryReceivedDownloadHttpInput() { }
}
