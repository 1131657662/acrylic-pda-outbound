package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Global counter history within a trusted loss interval. This evidence never opens ordinary numbering. */
public final class RecoveryReleaseNumberHistory {
    private final JsonNode facts;
    private final String sha256;
    private RecoveryReleaseNumberHistory(JsonNode facts) {
        this.facts=facts.deepCopy(); sha256=RecoveryControlFormat.sha256(RecoveryTemporaryEvidenceMaterial.encode(facts));
    }

    public static RecoveryReleaseNumberHistory verify(RecoveryClosedAuthority authority,RecoveryReleaseInventory inventory,Request request) {
        require(authority!=null && inventory!=null && request!=null,"RECOVERY_NUMBER_HISTORY_REQUIRED");
        var coverage=RecoveryReleaseHistoryCoverage.verify(authority,inventory,request.archive(),request.keys());
        var history=request.archive().permissions().getFirst(); String schema=history.expected().database().schema();
        var before=RecoveryReleaseNumberSnapshot.read(authority,request.begin(),schema);
        var after=RecoveryReleaseNumberSnapshot.read(authority,request.end(),schema);
        var initial=RecoveryReleaseNumberSnapshot.counters(before.path("tables").path("order_number_counter"));
        var expected=RecoveryReleaseNumberSnapshot.counters(after.path("tables").path("order_number_counter"));
        var observed=RecoveryReleaseNumberSnapshot.counters(inventory.facts().path("tables").path("order_number_counter"));
        RecoveryReleaseNumberSnapshot.orders(before,initial); RecoveryReleaseNumberSnapshot.orders(after,expected);
        require(RecoveryReleaseNumberInventory.original(after).equals(inventory.facts().path("globalOrderNumbers")),
            "RECOVERY_NUMBER_TARGET_ORDERS_CHANGED");
        var replay=RecoveryNumberBinlogRows.replay(history,initial);
        require(replay.counters().equals(expected),"RECOVERY_NUMBER_HISTORY_ENDPOINT_MISMATCH");
        require(expected.equals(observed),"RECOVERY_NUMBER_HISTORY_TARGET_MISMATCH");
        var result=coverage.facts(); var facts=SubmissionJson.object().put("formatVersion",1).put("historyCoverageSha256",coverage.sha256())
            .put("inventorySha256",inventory.sha256()).put("beginSnapshotSha256",request.begin().independentSha256())
            .put("endSnapshotSha256",request.end().independentSha256()).put("allocatedInWindow",Long.toString(replay.allocations()));
        facts.set("history",result); var counters=facts.putArray("globalCounters");
        replay.counters().forEach((date,value)->counters.addObject().put("businessDate",date.toString()).put("lastSequence",value.toString()));
        require(before.equals(RecoveryReleaseNumberSnapshot.read(authority,request.begin(),schema))
            && after.equals(RecoveryReleaseNumberSnapshot.read(authority,request.end(),schema)),"RECOVERY_NUMBER_SNAPSHOT_CHANGED");
        require(text(result,"controlRecordSha256").equals(authority.read().recordSha256()),"RECOVERY_NUMBER_CONTROL_CHANGED");
        return new RecoveryReleaseNumberHistory(facts);
    }

    public record Snapshot(Path file,String independentSha256) { }
    public record Request(RecoveryReceivedTemporaryArchiveConsumer.Request archive,RecoveryReceiptKeys keys,Snapshot begin,Snapshot end) { }
    public JsonNode facts() { return facts.deepCopy(); }
    public String sha256() { return sha256; }
    public boolean releaseAllowed() { return false; }
    public boolean ordinaryWritesAllowed() { return false; }
    public boolean localDeletionAllowed() { return false; }
}
