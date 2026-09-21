package com.acrylic.outbound.foundationmigration;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Only the planned nullable K.order_id and owner CHECK replacement may alter old definitions. */
final class LegacySchema {
    private final JdbcTemplate jdbc;
    LegacySchema(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void verify(JsonNode originals) {
        var tables = originals.fieldNames();
        while (tables.hasNext()) {
            String table = tables.next();
            String current = jdbc.queryForObject("SHOW CREATE TABLE " + LegacyRows.name(table), (rs, n) -> rs.getString(2));
            var actual = definitions(current);
            for (String old : definitions(originals.path(table).asText())) {
                if (table.equals("bale_commitment") && old.startsWith("`order_id` ")) {
                    assertThat(actual).contains("`order_id` binary(16) DEFAULT NULL");
                } else if (table.equals("bale_commitment") && old.startsWith("CONSTRAINT `ck_commitment_owner` ")) {
                    assertThat(actual.stream().filter(row -> row.startsWith("CONSTRAINT `ck_commitment_owner` ")))
                            .singleElement().asString().contains("SOURCE_RESERVED");
                } else assertThat(actual).as("Unchanged definition %s: %s", table, old).contains(old);
            }
        }
    }

    private Set<String> definitions(String ddl) {
        var result = new LinkedHashSet<String>();
        for (String line : ddl.split("\\R")) {
            if (!line.startsWith("  ")) continue;
            result.add(line.trim().replaceFirst(",$", ""));
        }
        return result;
    }
}
