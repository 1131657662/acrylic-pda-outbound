package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual context route must re-prove the fault/archive; reads cannot weaken the fence or write a decision. */
final class HandoverFaultReadChecks {
    static void verify(HandoverTemporaryFaultFenceChecks f) throws Exception {
        String path = "/handovers/" + f.source.kase + "/context?warehouseId=" + f.source.head.warehouse() + "&commandType=FAULT_FENCE";
        var original = SubmissionJson.parse(Files.readAllBytes(f.directory.resolve("fault-receipt.json")));
        var facts = new HandoverFaultFacts(f.source.jdbc, f.directory.resolve("read-facts"));
        var before = facts.snapshot("before");
        var read = SubmissionJson.parse(f.faultReviewer.get(path));
        assertThat(read.path("fault").path("entryAvailable").asBoolean()).isFalse();
        assertThat(read.path("fault").path("receipt")).isEqualTo(original);
        f.faultReviewer.get("/handovers/" + f.source.kase + "/context?warehouseId=" + java.util.UUID.randomUUID()
            + "&commandType=FAULT_FENCE", 404, "HANDOVER_CASE_NOT_FOUND");
        var payload = f.directory.getParent().getParent().resolve("storage/submissions")
            .resolve(original.path("businessDecision").path("payloadSha256").asText() + ".json");
        byte[] bytes = Files.readAllBytes(payload);
        try {
            Files.write(payload, new byte[]{'{', '}'});
            Files.write(f.directory.resolve("read-corrupt-payload.json"), new byte[]{'{', '}'});
            f.faultReviewer.get(path, 409, "HANDOVER_STOP_FILE_UNAVAILABLE");
        } finally { Files.write(payload, bytes); }
        assertThat(SubmissionJson.parse(f.faultReviewer.get(path)).path("fault").path("receipt")).isEqualTo(original);
        assertThat(facts.snapshot("after")).isEqualTo(before);
        Files.writeString(f.directory.resolve("read-verified.marker"), "Actual context signed fault/custody/archive read, cross-warehouse and corrupt original refusal, no durable writes.\n");
    }
}
