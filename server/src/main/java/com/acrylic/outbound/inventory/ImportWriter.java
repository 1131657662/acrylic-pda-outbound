package com.acrylic.outbound.inventory;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.common.IdempotencyRequests;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.common.ServerState;
import com.acrylic.outbound.inventory.parser.ImportPreview;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportWriter {
    private final JdbcTemplate jdbc;
    private final ImportRowStore rows;
    private final AuditWriter audit;
    private final ServerState state;
    private final IdempotencyRequests requests;
    public ImportWriter(JdbcTemplate jdbc, ImportRowStore rows, AuditWriter audit, ServerState state, IdempotencyRequests requests) {
        this.jdbc = jdbc; this.rows = rows; this.audit = audit; this.state = state; this.requests = requests;
    }

    @Transactional
    public UUID save(Prepared input) {
        state.lockWritable(input.epoch());
        jdbc.queryForObject("SELECT id FROM warehouse WHERE id=? FOR UPDATE", byte[].class, Ids.bytes(input.warehouse()));
        var key = new IdempotencyRequests.Key(input.actor(), "inventory.import", input.key());
        UUID replay = requests.find(key, input.requestSha());
        if (replay != null) return replay;
        var existing = jdbc.queryForList("""
            SELECT BIN_TO_UUID(id) FROM inventory_import WHERE warehouse_id=? AND file_sha256=? AND parser_version=?
            """, String.class, Ids.bytes(input.warehouse()), input.file().sha256(), input.preview().parserVersion());
        UUID id = existing.isEmpty() ? create(input) : UUID.fromString(existing.getFirst());
        requests.record(key, input.requestSha(), id);
        audit.append(new AuditWriter.Event(input.actor(), existing.isEmpty() ? "INVENTORY_PREFLIGHT" : "IMPORT_FILE_REUSED",
            "INVENTORY_IMPORT", id, "{\"parserVersion\":\"" + input.preview().parserVersion() + "\"}"), input.requestId());
        return id;
    }

    private UUID create(Prepared input) {
        UUID id = Ids.next();
        ImportPreview p = input.preview();
        jdbc.update("""
            INSERT INTO inventory_import(id,warehouse_id,original_filename,file_key,file_sha256,parser_version,status,
              total_rows,valid_rows,error_rows,duplicate_rows,ignored_rows,product_count,total_weight_g,created_by,created_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(3))
            """, Ids.bytes(id), Ids.bytes(input.warehouse()), input.filename(), input.file().key(), input.file().sha256(),
            p.parserVersion(), p.status(), p.totalRows(), p.validRows(), p.errorRows(), p.duplicateRows(), p.ignoredRows(),
            p.productCount(), Long.parseLong(p.totalWeightGrams()), Ids.bytes(input.actor()));
        rows.insert(id, p.rows());
        return id;
    }
    public record Prepared(UUID warehouse, UUID actor, String key, String requestSha, String filename,
                           ImportFiles.Stored file, ImportPreview preview, String requestId, String epoch) { }
}
