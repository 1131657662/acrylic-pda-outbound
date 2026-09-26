package com.acrylic.outbound.temporary.evidence;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SourceEvidenceIntakeReadStoreTest {
    @Test void openReceiptWithNullableObjectReferencesIsReadable() {
        var jdbc=mock(JdbcTemplate.class);
        var row=new HashMap<String,Object>();
        row.put("object_id",null);
        row.put("evidence_set_id",null);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(row));

        var stored=new SourceEvidenceIntakeReadStore(jdbc).find(UUID.randomUUID(),UUID.randomUUID());

        assertThat(stored).containsKey("object_id");
        assertThat(stored.get("object_id")).isNull();
    }
}
