package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Real database + original Android body checks; every injected database mutation rolls back. */
public final class E2aAbortedEvidenceChecks {
    private final JdbcTemplate jdbc;
    private final SourceEvidenceIntakeTransactions transactions;
    private final SourceEvidenceIntakeBusinessFacts facts;
    private final SourceEvidenceIntakeRawObjects objects;
    private final SourceEvidenceIntakeObjectFiles files;
    private final SourceEvidenceIntakePageObjects.Binding binding;
    private final List<E2aPageItem> items;
    private final UUID set;
    private final long through, capture;
    private final E2aPageItem frozen;

    public E2aAbortedEvidenceChecks(ApplicationContext context, String submission) {
        jdbc = context.getBean(JdbcTemplate.class); transactions = context.getBean(SourceEvidenceIntakeTransactions.class);
        facts = context.getBean(SourceEvidenceIntakeBusinessFacts.class);
        objects = context.getBean(SourceEvidenceIntakeRawObjects.class); files = context.getBean(SourceEvidenceIntakeObjectFiles.class);
        var row = jdbc.queryForMap("SELECT * FROM source_evidence_intake_receipt WHERE submission_id=UUID_TO_BIN(?) AND operation_kind='OPEN'", submission);
        binding = new SourceEvidenceIntakePageObjects.Binding(id(row, "warehouse_id"), id(row, "scope_id"),
            UUID.fromString(submission), 1, id(row, "creator_device_id"), id(row, "principal_id"), id(row, "original_key"));
        var evidence = jdbc.queryForMap("SELECT * FROM source_evidence_set WHERE submission_id=UUID_TO_BIN(?)", submission);
        set = id(evidence, "id"); through = ((Number) evidence.get("audit_through_seq")).longValue();
        capture = ((Number) evidence.get("capture_high_watermark")).longValue(); items = items();
        frozen = items.stream().filter(item -> "LOCAL_FROZEN_PAYLOAD".equals(item.objectKind())).findFirst().orElseThrow();
    }

    public void verify() {
        valid();
        var missing = new ArrayList<>(items); missing.remove(frozen);
        reject("E2A_LOCAL_PAYLOAD_MISSING_OR_DUPLICATE", () -> check(missing));
        var duplicate = new ArrayList<>(items); duplicate.add(frozen);
        reject("E2A_LOCAL_PAYLOAD_MISSING_OR_DUPLICATE", () -> check(duplicate));
        reject("E2A_LOCAL_PAYLOAD_SOURCE_CONFLICT", () -> facts.require(
            new SourceEvidenceIntakePageObjects.Binding(UUID.randomUUID(), binding.scopeId(), binding.submissionId(),
                1, binding.creatorDeviceId(), binding.originalOperatorId(), binding.openKey()), items, capture, set, through));
        reject("E2A_LOCAL_PAYLOAD_BINDING_CONFLICT", () -> facts.require(binding, items, capture, set, through + 1));
        altered("E2A_LOCAL_PAYLOAD_SOURCE_CONFLICT", body -> body.put("submissionId", UUID.randomUUID().toString()));
        altered("E2A_AUDIT_PREFIX_UNVERIFIABLE", body -> ((ObjectNode) body.withArray("events").get(0)).put("detail", "forged"));
        altered("INVALID_SUBMISSION", body -> ((ObjectNode) body.withArray("scans").get(0)).put("weightGrams", "999999"));
        altered("INVALID_SUBMISSION", body -> ((ObjectNode) body.withArray("captures").get(0)).put("operatorId", UUID.randomUUID().toString()));
        conflicts();
        valid();
    }

    private void conflicts() {
        reject("E2A_LOCAL_CAPTURE_ALREADY_SUBMITTED", () -> {
            var original = items.stream().filter(item -> "CAPTURE".equals(item.itemKind())).findFirst().orElseThrow();
            var order = jdbc.queryForObject("SELECT id FROM outbound_order LIMIT 1", byte[].class);
            jdbc.update("INSERT INTO submitted_capture(id,submission_id,order_id,origin_device_id,device_capture_seq,order_scan_seq,record_sha256) VALUES(?,?,?,?,?,?,?)", Ids.bytes(original.originalRecordId()),
                Ids.bytes(binding.submissionId()), order, Ids.bytes(binding.creatorDeviceId()),
                original.originalDeviceSeq(), 1, original.recordSha256()); check(items);
        });
        reject("E2A_BALE_CYCLE_CONFLICT", () -> {
            var scan = items.stream().filter(item -> "SCAN".equals(item.itemKind())).findFirst().orElseThrow();
            jdbc.update("UPDATE bale SET dispatch_cycle=dispatch_cycle+1 WHERE id=?", Ids.bytes(scan.baleId())); check(items);
        });
        reject("E2A_LOCAL_PAYLOAD_SOURCE_CONFLICT", () -> {
            jdbc.update("UPDATE submission SET status='REVIEW_REQUIRED' WHERE id=?", Ids.bytes(binding.submissionId())); check(items);
        });
    }

    private void altered(String code, Consumer<ObjectNode> edit) {
        boolean declared = jdbc.queryForObject("SELECT COUNT(*) FROM submission_upload WHERE submission_id=?",
            Integer.class, Ids.bytes(binding.submissionId())) > 0;
        reject(declared && !code.equals("E2A_LOCAL_PAYLOAD_SOURCE_CONFLICT") ? "IDEMPOTENCY_CONTENT_MISMATCH" : code, () -> {
            var key = new SourceEvidenceIntakeRawObjects.Binding(binding.warehouseId(), binding.scopeId(),
                binding.submissionId(), 1, binding.creatorDeviceId(), binding.originalOperatorId(), frozen.objectKind(), frozen.originalObjectId());
            try {
                var body = (ObjectNode) SubmissionJson.parse(files.read(objects.findAfterSourceLock(key).stored()));
                edit.accept(body); byte[] bytes = SubmissionJson.canonical(body).getBytes(StandardCharsets.UTF_8);
                var stored = files.store(new ByteArrayInputStream(bytes), bytes.length, ImportFiles.sha256(bytes));
                var objectId = UUID.randomUUID();
                objects.insert(new SourceEvidenceIntakeRawObjects.Binding(binding.warehouseId(), binding.scopeId(),
                    binding.submissionId(), 1, binding.creatorDeviceId(), binding.originalOperatorId(), frozen.objectKind(), objectId), stored, Instant.now());
                var modified = new ArrayList<>(items); modified.remove(frozen);
                modified.add(new E2aPageItem("STOP", binding.submissionId(), frozen.objectKind(), objectId, stored.sha256(),
                    bytes.length, through, stored.sha256(), "FROZEN", null, null, null)); check(modified);
            } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        });
    }

    private void valid() {
        transactions.write(status -> {
            assertThat(facts.require(binding, items, capture, set, through)).hasSize(1); status.setRollbackOnly(); return null;
        });
    }

    private void check(List<E2aPageItem> input) { facts.require(binding, input, capture, set, through); }

    private void reject(String code, Runnable action) {
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment", Integer.class);
        assertThatThrownBy(() -> transactions.write(status -> { status.setRollbackOnly(); action.run(); return null; }))
            .isInstanceOfSatisfying(BusinessException.class, failure -> assertThat(failure.code()).isEqualTo(code));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment", Integer.class)).isEqualTo(before);
    }

    private List<E2aPageItem> items() {
        return jdbc.query("""
            SELECT i.*,o.original_object_id,o.kind,o.byte_count,o.bytes_sha256 FROM source_evidence_item i
            JOIN source_evidence_object o ON o.id=i.object_id WHERE i.set_id=? ORDER BY i.item_kind,i.original_device_seq
            """, (r, n) -> new E2aPageItem(r.getString("item_kind"), uuid(r.getBytes("original_record_id")),
            r.getString("kind"), uuid(r.getBytes("original_object_id")), r.getString("bytes_sha256"),
            r.getInt("byte_count"), r.getLong("original_device_seq"), r.getString("record_sha256"), r.getString("record_status"),
            nullable(r.getBytes("bale_id")), (Long) r.getObject("original_cycle"), nullable(r.getBytes("capture_id"))), Ids.bytes(set));
    }

    private static UUID id(java.util.Map<String, Object> row, String key) { return uuid((byte[]) row.get(key)); }
    private static UUID nullable(byte[] bytes) { return bytes == null ? null : uuid(bytes); }
    private static UUID uuid(byte[] bytes) { var buffer = java.nio.ByteBuffer.wrap(bytes); return new UUID(buffer.getLong(), buffer.getLong()); }
}
