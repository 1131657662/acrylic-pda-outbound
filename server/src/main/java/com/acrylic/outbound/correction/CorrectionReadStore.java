package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.Ids;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Only reads the immutable original command records. */
@Repository
class CorrectionReadStore {
    private final JdbcTemplate jdbc;
    CorrectionReadStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Map<String, Object> originalCreate(UUID document) {
        var rows = jdbc.queryForList("SELECT * FROM correction_command_receipt WHERE document_id=? AND command_type='CREATE'",
            Ids.bytes(document));
        if (rows.size() != 1) throw CorrectionCommand.corrupt();
        return rows.getFirst();
    }
}
