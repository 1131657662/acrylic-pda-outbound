package com.acrylic.outbound.relay;

import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Fixed canonical command identity; bounded compatibility with original Map.of serialization. */
final class RelayRequestDigest {
    private RelayRequestDigest() { }
    static String text(Map<String,String> content) {
        var node=SubmissionJson.object();content.forEach(node::put);return SubmissionJson.canonical(node);
    }
    static String digest(Map<String,String> content) { return sha(text(content)); }
    static boolean legacyMatches(RelayJournal.Command command,String digest) {
        Set<String> expected=switch(command.key().scope()) {
            case "relay.receive" -> Set.of("archiveSha256","warehouseId");
            case "relay.process" -> Set.of("relayId","archiveSha256","submissionId","collectorId","epoch");
            default -> Set.of();
        };
        if(expected.isEmpty()||!command.content().keySet().equals(expected))return false;
        return permutations(new Legacy(command.content(),digest),new ArrayList<>(expected),new LinkedHashMap<>());
    }
    private static boolean permutations(Legacy legacy,List<String> remaining,LinkedHashMap<String,String> ordered) {
        if(remaining.isEmpty()) {
            var node=SubmissionJson.object();ordered.forEach(node::put);return sha(node.toString()).equals(legacy.digest());
        }
        for(String key:remaining) {
            var next=new ArrayList<>(remaining);next.remove(key);ordered.put(key,legacy.content().get(key));
            if(permutations(legacy,next,ordered))return true;
            ordered.remove(key);
        }
        return false;
    }
    private static String sha(String text) { return ImportFiles.sha256(text.getBytes(StandardCharsets.UTF_8)); }
    private record Legacy(Map<String,String> content,String digest) { }
}
