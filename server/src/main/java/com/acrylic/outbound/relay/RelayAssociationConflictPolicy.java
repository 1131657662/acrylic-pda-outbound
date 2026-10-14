package com.acrylic.outbound.relay;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** A recorded ownership proof must apply to the entire live set; no capability is inferred. */
final class RelayAssociationConflictPolicy {
    private RelayAssociationConflictPolicy() { }
    static void complete(JsonNode context,JsonNode decision,UUID actor,Instant now) {
        boolean overlap=false;
        for(var peer:context.path("peers")) {
            if(peer.path("closureDivergence").asBoolean())throw RelayAssociationPolicy.blocked("结束分歧尚未处置");
            overlap|=peer.path("crossTaskConflict").asBoolean();
        }
        if(!overlap) {
            if(decision!=null)throw RelayAssociationPolicy.invalid("无跨任务冲突不得引用无关决定");return;
        }
        if(decision==null)throw RelayAssociationPolicy.blocked("跨任务重码须显式引用完整原冲突决定");
        var original=decision.path("originalContext");var selected=original.path("selected");
        if(!selected.path("context").equals(context))throw RelayAssociationPolicy.changed();
        var groups=RelayConflictPolicy.peers(decision.path("evidence"),context);var peers=new TreeMap<String,JsonNode>();
        var reviewer=new RelayConflictPolicy.Reviewer(actor,now);RelayConflictPolicy.check(context,selected.path("physical"),reviewer);
        for(var peer:original.path("peers")) {
            var group=peer.path("group");
            if(!groups.contains(group))throw RelayAssociationPolicy.changed();
            RelayConflictPolicy.peerIdentity(peer.path("context"),group);RelayConflictPolicy.check(peer.path("context"),peer.path("physical"),reviewer);
            if(peers.put(text(group,"deviceId")+"/"+text(group,"taskId"),peer)!=null)throw RelayAssociationPolicy.changed();
        }
        if(peers.size()!=groups.size())throw RelayAssociationPolicy.changed();
        var claims=object().putArray("claims");RelayConflictPolicy.claims(context,selected.path("physical"),peers).forEach(claims::add);
        if(!claims.equals(original.path("claims")))throw RelayAssociationPolicy.changed();
    }
}
