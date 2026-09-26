package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Consumes the durable pre-stop device prefix; later events cannot repair a missing fact. */
@Component
public class UnsubmittedSourceAudits {
    private final JdbcTemplate jdbc;
    private final SourceEvidenceIntakeAuditPrefix prefix;

    public UnsubmittedSourceAudits(JdbcTemplate jdbc, SourceEvidenceIntakeAuditPrefix prefix) {
        this.jdbc = jdbc; this.prefix = prefix;
    }

    Map<UUID, List<JsonNode>> read(SourceEvidenceIntakePageObjects.Binding binding, long through, Set<UUID> captures) {
        require(through > 0, "其他来源缺少停止审计前缀");
        long epoch = jdbc.queryForObject("SELECT source_recovery_epoch FROM submission WHERE id=?", Long.class,
            Ids.bytes(binding.submissionId()));
        prefix.require(binding.creatorDeviceId(), binding.warehouseId(), epoch, through);
        var result = new HashMap<UUID, List<JsonNode>>();
        long cursor = 0;
        while (cursor < through) {
            var rows = jdbc.queryForList("""
                SELECT payload FROM pda_audit_event WHERE origin_device_id=? AND local_seq>? AND local_seq<=?
                ORDER BY local_seq LIMIT 512
                """, String.class, Ids.bytes(binding.creatorDeviceId()), cursor, through);
            require(!rows.isEmpty(), "其他来源审计前缀缺失");
            for (var raw : rows) {
                var event = parse(raw);
                require(number(event, "localSeq") == cursor + 1, "其他来源审计前缀缺口");
                cursor++;
                if (event.path("captureId").isNull()) continue;
                UUID id = uuid(event, "captureId");
                if (captures.contains(id)) result.computeIfAbsent(id, ignored -> new ArrayList<>()).add(event);
            }
        }
        return result;
    }
}
