package com.acrylic.outbound.relay;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.UUID;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

final class RelayPhysicalFixture {
    static final Instant NOW=Instant.parse("2026-10-14T06:00:00Z");
    final UUID collector=UUID.randomUUID(),supervisor=UUID.randomUUID(),capture=UUID.randomUUID();
    final RelayPhysicalStore.Scope scope=new RelayPhysicalStore.Scope(UUID.randomUUID(),UUID.randomUUID(),supervisor);
    final ObjectNode context=object(),request=object();
    RelayPhysicalFixture() {
        context.putObject("original").put("operatorId",collector.toString()).put("taskId",UUID.randomUUID().toString())
            .put("archiveSha256","a".repeat(64)).put("closureSha256","b".repeat(64));
        context.putArray("inventory").addObject().put("captureId",capture.toString());
        request.put("contextSha256",sha(context)).put("checkedAt",NOW.toString()).put("area","隔离合成车厢").put("reason","合成核对");
        request.putArray("packages").addObject().put("captureId",capture.toString()).put("status","LOADED").put("note","");
        request.putArray("attachments").addObject().put("attachmentId",UUID.randomUUID().toString())
            .put("evidenceSha256","c".repeat(64)).put("sizeBytes","1");
    }
    ObjectNode observation() { return (ObjectNode)request.path("packages").get(0); }
    ObjectNode attachment() { return (ObjectNode)request.path("attachments").get(0); }
}
