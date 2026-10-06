package com.acrylic.outbound.temporary.continuation;

import com.acrylic.outbound.temporary.command.TemporaryRetainedManifest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static com.acrylic.outbound.temporary.continuation.TemporaryRoundFacts.conflict;

/** Exact old byte facts remain prefixes, even when their source/authorization differs from the new round. */
final class TemporaryRoundPrefixes {
    private TemporaryRoundPrefixes() { }
    static void require(JsonNode previous,JsonNode body,List<TemporaryRetainedManifest.Expected> retained) {
        var captureIds=new HashSet<String>();array(previous,"captures").forEach(c->captureIds.add(text(c,"id")));
        for(String key:List.of("captures","scans","events"))prefix(array(previous,key),array(body,key));
        var scans=array(body,"scans");
        for(int i=array(previous,"scans").size();i<scans.size();i++)
            if(captureIds.contains(text(scans.get(i),"captureId")))throw conflict("TEMP_CONTINUATION_RETAINED_RECORD_CHANGED");
        var events=array(body,"events");
        for(int i=array(previous,"events").size();i<events.size();i++)
            if(captureIds.contains(events.get(i).path("captureId").asText()))throw conflict("TEMP_CONTINUATION_RETAINED_RECORD_CHANGED");
        var actual=new ArrayList<TemporaryRetainedManifest.Expected>();
        for(var scan:array(previous,"scans"))if("ACTIVE".equals(text(scan,"status")))
            actual.add(new TemporaryRetainedManifest.Expected(uuid(scan,"baleId"),number(scan,"expectedDispatchCycle"),sha(scan)));
        if(actual.isEmpty() || actual.size()!=retained.size() || actual.stream().map(TemporaryRetainedManifest.Expected::baleId).distinct().count()!=actual.size()
            || !new HashSet<>(actual).equals(new HashSet<>(retained)))throw conflict("TEMP_CONTINUATION_RETAINED_SET_MISMATCH");
    }
    private static void prefix(JsonNode previous,JsonNode next) {
        if(next.size()<previous.size())throw conflict("TEMP_CONTINUATION_PREDECESSOR_EVIDENCE_CHANGED");
        for(int i=0;i<previous.size();i++)if(!previous.get(i).equals(next.get(i)))
            throw conflict("TEMP_CONTINUATION_PREDECESSOR_EVIDENCE_CHANGED");
    }
}
