package com.acrylic.outbound.relay;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** An extension can preserve an owner and add evidence, never replace an earlier allocation. */
final class RelayConflictExtensionPolicy {
    static final int MAX_CHAIN=200;
    private RelayConflictExtensionPolicy() { }
    static void complete(JsonNode context,RelayConflictStore.Row previous) {
        var old=previous.context();
        if(!old.path("selected").path("context").path("original").equals(context.path("selected").path("context").path("original")))
            throw RelayConflictPolicy.blocked("扩展不得改变选定原件、设备或任务归属");
        if(old.path("selected").path("context").equals(context.path("selected").path("context")))
            throw RelayConflictPolicy.blocked("完整依据未变化，请恢复原决定，不得用新键复制");
        if(!claims(context).containsAll(claims(old)) || !originals(context).containsAll(originals(old)))
            throw RelayConflictPolicy.blocked("扩展不得移除原包周期归属或任何原冲突方原件");
    }
    static void reference(RelayConflictStore.Row row,RelayConflictStore.Row previous) {
        if(!row.request().has("predecessorDecisionId") || !uuid(row.request(),"predecessorDecisionId").equals(previous.id())
            || !row.context().path("predecessorReceipt").equals(previous.receipt())
            || !row.scope().warehouse().equals(previous.scope().warehouse()) || !row.scope().relay().equals(previous.scope().relay())
            || !row.scope().device().equals(previous.scope().device()) || !row.scope().task().equals(previous.scope().task()))
            throw RelayConflictStore.corrupt();
        complete(row.context(),previous);
    }
    private static Set<String> claims(JsonNode context) {
        var result=new HashSet<String>();context.path("claims").forEach(c->result.add(canonical(c)));return result;
    }
    private static Set<String> originals(JsonNode context) {
        var result=new HashSet<String>();
        context.path("peers").forEach(p->p.path("group").path("originals").forEach(o->result.add(canonical(o))));return result;
    }
}
