package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.Path;
import java.util.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic candidate rows test bounded discovery only; they cannot substitute for original signed action evidence. */
public final class ReturnedStockPagingChecks {
    public static void verify(WebApplicationContext context,Path attempt) throws Exception {
        var f = new ReturnedStockHttpFixture(context,attempt,"paging");
        var ids = new HashSet<String>(); ids.add(f.original.dispatch);
        for (int i=0;i<51;i++) ids.add(candidate(f,i).toString());
        String path = "/corrections/reconciliation?warehouseId="+f.original.warehouse;
        var first = SubmissionJson.parse(f.maker.get(path,200));
        assertThat(first.path("returnedCandidates").size()).isEqualTo(50);
        String cursor = first.path("nextReturnedCursor").asText();
        assertThat(cursor).isEqualTo(first.path("returnedCandidates").get(49).path("dispatchId").asText());
        var second = SubmissionJson.parse(f.maker.get(path+"&beforeReturned="+cursor,200));
        assertThat(second.path("returnedCandidates").size()).isEqualTo(2);
        assertThat(second.path("nextReturnedCursor").isNull()).isTrue();
        var observed = new ArrayList<String>();
        first.path("returnedCandidates").forEach(row -> observed.add(row.path("dispatchId").asText()));
        second.path("returnedCandidates").forEach(row -> observed.add(row.path("dispatchId").asText()));
        assertThat(observed).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(ids);
        String synthetic = first.path("returnedCandidates").get(0).path("dispatchId").asText();
        var refused = SubmissionJson.parse(f.maker.get("/corrections/reconciliation/context?warehouseId="+f.original.warehouse
            + "&commandType=RECONCILE_RETURN_DISCOVER&dispatchId="+synthetic,409));
        assertThat(refused.path("code").asText()).isEqualTo("CORRECTION_RECEIPT_INTEGRITY_FAILURE");
        java.nio.file.Files.writeString(f.original.evidence.resolve("paging.json"),SubmissionJson.object().set("first",first).toPrettyString());
    }
    private static UUID candidate(ReturnedStockHttpFixture f,int index) {
        UUID bale = Ids.next(),capture = Ids.next(),dispatch = Ids.next(),line = Ids.next();
        var source = f.original.jdbc.queryForMap("SELECT BIN_TO_UUID(capture_id) capture FROM dispatch_record WHERE id=UUID_TO_BIN(?)",f.original.dispatch);
        var reopening = f.original.jdbc.queryForMap("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(correction_line_id) line FROM cycle_reopening WHERE original_dispatch_id=UUID_TO_BIN(?)",f.original.dispatch);
        clone(f,"bale","id",f.bale,Map.of("id",Ids.bytes(bale),"barcode",String.format("003P%07d",index)));
        clone(f,"inventory_snapshot_bale","bale_id",f.bale,Map.of("bale_id",Ids.bytes(bale)));
        clone(f,"inventory_position","bale_id",f.bale,Map.of("bale_id",Ids.bytes(bale)));
        clone(f,"submitted_capture","id",source.get("capture").toString(),Map.of("id",Ids.bytes(capture),"device_capture_seq",index+100,"order_scan_seq",index+100));
        clone(f,"dispatch_record","id",f.original.dispatch,Map.of("id",Ids.bytes(dispatch),"capture_id",Ids.bytes(capture),"bale_id",Ids.bytes(bale),"order_scan_seq",index+100));
        clone(f,"correction_line","id",reopening.get("line").toString(),Map.of("id",Ids.bytes(line),"dispatch_id",Ids.bytes(dispatch),"bale_id",Ids.bytes(bale),"line_no",index+2));
        clone(f,"cycle_reopening","id",reopening.get("id").toString(),Map.of("id",Ids.bytes(Ids.next()),"original_dispatch_id",Ids.bytes(dispatch),"correction_line_id",Ids.bytes(line)));
        return dispatch;
    }
    private static void clone(ReturnedStockHttpFixture f,String table,String key,String id,Map<String,Object> overrides) {
        if (!Set.of("bale","inventory_snapshot_bale","inventory_position","submitted_capture","dispatch_record","correction_line","cycle_reopening").contains(table)
            || !Set.of("id","bale_id").contains(key)) throw new AssertionError("unknown test table");
        var row = new LinkedHashMap<>(f.original.jdbc.queryForMap("SELECT * FROM "+table+" WHERE "+key+"=UUID_TO_BIN(?)",id));
        row.putAll(overrides);
        String columns = String.join(",",row.keySet().stream().map(name -> "`"+name+"`").toList());
        String values = String.join(",",Collections.nCopies(row.size(),"?"));
        f.original.jdbc.update("INSERT INTO "+table+"("+columns+") VALUES("+values+")",row.values().toArray());
    }
    private ReturnedStockPagingChecks() { }
}
