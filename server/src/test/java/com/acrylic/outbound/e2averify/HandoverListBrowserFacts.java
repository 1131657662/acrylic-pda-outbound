package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Browser logins may append auth audits; handover reads cannot change business facts or earlier audits. */
final class HandoverListBrowserFacts {
    static void unchanged(ObjectNode before, ObjectNode after, UUID browserActor) throws Exception {
        var expected = before.deepCopy(); var actual = after.deepCopy();
        var previousAudits = rows(expected.remove("operation_event"));
        var finalAudits = rows(actual.remove("operation_event"));
        assertThat(actual).as("all fifteen business tables remain byte-for-byte identical").isEqualTo(expected);
        assertThat(finalAudits).as("no previous audit changed or disappeared").containsAll(previousAudits);
        var added = finalAudits.stream().filter(row -> !previousAudits.contains(row)).toList();
        assertThat(added).as("one real login per isolated browser scenario").hasSize(6);
        var mapper = new ObjectMapper();
        String actor = Base64.getEncoder().encodeToString(Ids.bytes(browserActor));
        for (String row : added) {
            var audit = mapper.readTree(row);
            assertThat(audit.at("/event_type/value").asText()).isEqualTo("WEB_LOGIN");
            assertThat(audit.at("/entity_type/value").asText()).isEqualTo("USER");
            assertThat(audit.at("/actor_id/value").asText()).isEqualTo(actor);
            assertThat(audit.at("/entity_id/value").asText()).isEqualTo(actor);
        }
    }

    private static List<String> rows(JsonNode array) {
        var rows = new ArrayList<String>(); array.forEach(row -> rows.add(row.asText())); return rows;
    }
}
