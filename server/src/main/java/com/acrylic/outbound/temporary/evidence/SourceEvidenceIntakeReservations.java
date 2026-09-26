package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.sync.BarcodeChanges;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SourceEvidenceIntakeReservations {
    private static final String FIND = """
        SELECT state,dispatch_cycle,BIN_TO_UUID(submission_id) AS owner,
          BIN_TO_UUID(source_scope_id) AS source_scope,BIN_TO_UUID(source_warehouse_id) AS source_warehouse,
          BIN_TO_UUID(source_evidence_set_id) AS evidence_set
        FROM bale_commitment WHERE bale_id=? FOR UPDATE
        """;
    private static final String INSERT = """
        INSERT INTO bale_commitment
        (bale_id,order_id,submission_id,dispatch_cycle,state,created_at,
         source_scope_id,source_warehouse_id,source_evidence_set_id)
        VALUES (?,NULL,NULL,?,'SOURCE_RESERVED',UTC_TIMESTAMP(3),?,?,?)
        """;
    private final JdbcTemplate jdbc;

    public SourceEvidenceIntakeReservations(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(SourceEvidenceIntakePageObjects.Binding binding, UUID setId, List<E2aPageItem> items) {
        var active = items.stream().filter(item -> "SCAN".equals(item.itemKind())
            && "ACTIVE".equals(item.recordStatus()))
            .sorted(Comparator.comparing(item -> item.baleId().toString())).toList();
        var seen = new HashSet<UUID>();
        if (active.stream().anyMatch(item -> !seen.add(item.baleId()))) throw conflict();
        for (var item : active) reserveOne(binding, setId, item);
    }

    private void reserveOne(SourceEvidenceIntakePageObjects.Binding binding, UUID setId, E2aPageItem item) {
        UUID bale = item.baleId();
        var rows = jdbc.queryForList(FIND, Ids.bytes(bale));
        if (rows.isEmpty()) {
            if (jdbc.update(INSERT, Ids.bytes(bale), item.originalCycle(), Ids.bytes(binding.scopeId()),
                Ids.bytes(binding.warehouseId()), Ids.bytes(setId)) != 1) throw conflict();
            BarcodeChanges.bale(jdbc, bale);
            return;
        }
        if (rows.size() != 1 || !same(binding, setId, item, rows.getFirst())) throw conflict();
    }

    private static boolean same(SourceEvidenceIntakePageObjects.Binding binding, UUID setId, E2aPageItem item,
        Map<String, Object> row) {
        if (!(row.get("dispatch_cycle") instanceof Number cycle) || cycle.longValue() != item.originalCycle()
            || !binding.scopeId().toString().equals(row.get("source_scope"))
            || !binding.warehouseId().toString().equals(row.get("source_warehouse"))) return false;
        return ("REVIEW_RESERVED".equals(row.get("state"))
            && binding.submissionId().toString().equals(row.get("owner")) && row.get("evidence_set") == null)
            || ("SOURCE_RESERVED".equals(row.get("state")) && row.get("owner") == null
                && setId.toString().equals(row.get("evidence_set")));
    }

    private static BusinessException conflict() {
        return new BusinessException(409, "E2A_BALE_OWNER_CONFLICT", "原机逐包占用与当前来源不一致");
    }
}
