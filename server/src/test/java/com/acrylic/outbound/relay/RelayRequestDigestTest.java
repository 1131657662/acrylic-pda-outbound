package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.IdempotencyRequests;
import com.acrylic.outbound.common.JsonText;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RelayRequestDigestTest {
    @Test void sameCommandKeepsDigestAcrossDifferentJvmMapIterationOrders() {
        var first=new LinkedHashMap<String,String>();first.put("warehouseId","original-warehouse");first.put("archiveSha256","original-hash");
        var reverse=new LinkedHashMap<String,String>();reverse.put("archiveSha256","original-hash");reverse.put("warehouseId","original-warehouse");
        assertThat(RelayRequestDigest.digest(first)).isEqualTo(RelayRequestDigest.digest(reverse));
        assertThat(RelayRequestDigest.text(first)).isEqualTo("{\"archiveSha256\":\"original-hash\",\"warehouseId\":\"original-warehouse\"}");
        var command=new RelayJournal.Command(new IdempotencyRequests.Key(UUID.randomUUID(),"relay.receive","original-key-1234"),first);
        var legacy=new JsonText(new ObjectMapper()).digest(first);
        assertThat(RelayRequestDigest.legacyMatches(command,legacy)).isTrue();
        assertThat(RelayRequestDigest.legacyMatches(command,new JsonText(new ObjectMapper()).digest(Map.of("warehouseId","other","archiveSha256","original-hash")))).isFalse();
    }
    @Test void onlyExactOriginalProcessValuesAndKnownShapesMatchLegacyRequests() {
        var content=new LinkedHashMap<String,String>();
        for(String name:List.of("epoch","submissionId","archiveSha256","collectorId","relayId"))content.put(name,"original-"+name);
        var key=new IdempotencyRequests.Key(UUID.randomUUID(),"relay.process","original-key-1234");
        var command=new RelayJournal.Command(key,content);var json=new JsonText(new ObjectMapper());
        assertThat(RelayRequestDigest.legacyMatches(command,json.digest(content))).isTrue();
        for(String field:content.keySet()) {
            var other=new LinkedHashMap<>(content);other.put(field,"different");
            assertThat(RelayRequestDigest.legacyMatches(command,json.digest(other))).as(field).isFalse();
        }
        assertThat(RelayRequestDigest.legacyMatches(new RelayJournal.Command(key,Map.of("relayId","original-relayId")),json.digest(content))).isFalse();
    }
}
