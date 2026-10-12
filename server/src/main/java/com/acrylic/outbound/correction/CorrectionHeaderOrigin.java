package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.exports.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Projects business headers from the already verified frozen original; original result and receipt bytes stay untouched. */
@Component
public final class CorrectionHeaderOrigin {
    private final JdbcTemplate jdbc;
    private final ExportFacts facts;
    private final TemporaryExportSource temporary;
    private final PdaExportSource pda;
    private final PcExportSource pc;
    public CorrectionHeaderOrigin(JdbcTemplate jdbc, ExportFacts facts, TemporaryExportSource temporary, PdaExportSource pda, PcExportSource pc) {
        this.jdbc = jdbc; this.facts = facts; this.temporary = temporary; this.pda = pda; this.pc = pc;
    }

    public JsonNode base(UUID order) {
        try {
            UUID warehouse = UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(warehouse_id) FROM outbound_order WHERE id=?",
                String.class, Ids.bytes(order)));
            var head = facts.head(warehouse, order, 1);
            var source = head.pcSubmission() != null ? pc.read(head)
                : facts.tempOrigin(head.pdaSubmission()) ? temporary.read(head, false, Instant.now()) : pda.read(head, false);
            return SubmissionJson.object().put("vehicle", source.header().vehicle()).put("container", source.header().container())
                .put("remark", source.header().remark());
        } catch (RuntimeException damaged) { throw CorrectionCommand.corrupt(); }
    }

    public JsonNode normalize(UUID order, JsonNode snapshot) { return fill(snapshot, base(order)); }

    static ObjectNode fill(JsonNode snapshot, JsonNode base) {
        if (!snapshot.isObject()) throw CorrectionCommand.corrupt();
        ObjectNode result = snapshot.deepCopy();
        for (String key : java.util.List.of("vehicle", "container", "remark"))
            if (!result.has(key)) result.set(key, base.get(key));
        return result;
    }
}
