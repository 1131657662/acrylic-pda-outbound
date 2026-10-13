package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** All received originals are evidence. Multiple exports of a task never become multiple devices. */
@Component
public class RelayIndependentPeers {
    private final JdbcTemplate jdbc;private final RelayFiles files;private final RelayAccess access;
    public RelayIndependentPeers(JdbcTemplate jdbc,RelayFiles files,RelayAccess access) { this.jdbc=jdbc;this.files=files;this.access=access; }
    public List<ObjectNode> read(RelayArchive.Bundle selected) {
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(id),archive_sha256 FROM original_relay
            WHERE warehouse_id=? AND source_kind='INDEPENDENT' ORDER BY id LIMIT 201
            """,(rs,n)->new Original(rs.getString(1),rs.getString(2)),Ids.bytes(selected.original().warehouse()));
        RelayIndependentCandidateQuery.capacity(rows.size(),200);var groups=new TreeMap<String,ObjectNode>();long bytes=0;int[] records={0};
        for(var row:rows) {
            var raw=files.read(row.hash());bytes+=raw.length;RelayIndependentCandidateQuery.capacity((int)bytes,128*1024*1024);
            var bundle=RelayArchive.read(raw);records[0]+=bundle.evidence().scans().size();RelayIndependentCandidateQuery.capacity(records[0],100000);
            var key=key(bundle);var group=groups.computeIfAbsent(key,ignored->group(bundle));
            boolean trusted=access.trusted(bundle,false);
            group.withArray("originals").add(object().put("relayId",row.id()).put("exportId",bundle.exportId().toString())
                .put("archiveSha256",row.hash()).put("sourceTrust",trusted?"TRUSTED":"UNVERIFIED")
                .put("completeBoundaryVerified",trusted && bundle.closure()!=null).put("closureSha256",bundle.closure()==null?null:sha(bundle.closure())));
            merge(group,bundle,selected);
        }
        return List.copyOf(groups.values());
    }
    static ObjectNode group(RelayArchive.Bundle bundle) {
        var group=object().put("deviceId",bundle.original().device().toString()).put("taskId",bundle.original().localOrder().toString());
        group.putArray("originals");group.putArray("overlappingBarcodes");group.putArray("closureDigests");return group;
    }
    static void merge(ObjectNode group,RelayArchive.Bundle bundle,RelayArchive.Bundle selected) {
        var active=new HashSet<String>();selected.evidence().active().forEach(scan->active.add(text(scan,"barcode")));
        var barcodes=new TreeSet<String>();group.withArray("overlappingBarcodes").forEach(value->barcodes.add(value.asText()));
        bundle.evidence().active().stream().map(scan->text(scan,"barcode")).filter(active::contains).forEach(barcodes::add);
        var values=group.putArray("overlappingBarcodes");barcodes.forEach(values::add);
        var digests=new TreeSet<String>();group.withArray("closureDigests").forEach(value->digests.add(value.asText()));
        if(bundle.closure()!=null)digests.add(sha(bundle.closure()));var hashes=group.putArray("closureDigests");digests.forEach(hashes::add);
        boolean same=key(bundle).equals(key(selected));
        group.put("selectedTask",same).put("closureDivergence",digests.size()>1).put("crossTaskConflict",!same && !barcodes.isEmpty())
            .put("crossDeviceConflict",!same && !barcodes.isEmpty() && !bundle.original().device().equals(selected.original().device()));
    }
    private static String key(RelayArchive.Bundle bundle) { return bundle.original().device()+"/"+bundle.original().localOrder(); }
    private record Original(String id,String hash) { }
}
