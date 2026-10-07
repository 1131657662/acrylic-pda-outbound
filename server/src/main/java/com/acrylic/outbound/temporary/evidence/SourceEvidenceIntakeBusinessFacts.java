package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SourceEvidenceIntakeBusinessFacts {
    private static final String STOCK = """
        SELECT BIN_TO_UUID(b.warehouse_id),b.dispatch_cycle,p.upstream_present,p.book_status,p.blocked_reason,
          BIN_TO_UUID(k.submission_id),k.dispatch_cycle,k.state,
           BIN_TO_UUID(k.source_scope_id),BIN_TO_UUID(k.source_warehouse_id),BIN_TO_UUID(k.source_evidence_set_id)
        FROM bale b LEFT JOIN inventory_position p ON p.bale_id=b.id
        LEFT JOIN bale_commitment k ON k.bale_id=b.id
        WHERE b.id=? FOR UPDATE
        """;
    private final JdbcTemplate jdbc;
    private final SubmissionFiles files;
    private final SourceEvidenceIntakeLocalPayload localPayload;
    private final UnsubmittedSourceFacts unsubmitted;

    public SourceEvidenceIntakeBusinessFacts(JdbcTemplate jdbc, SubmissionFiles files,
        SourceEvidenceIntakeLocalPayload localPayload, UnsubmittedSourceFacts unsubmitted) {
        this.jdbc = jdbc;
        this.files = files;
        this.localPayload = localPayload;
        this.unsubmitted = unsubmitted;
    }

    public List<E2aPageItem> require(SourceEvidenceIntakePageObjects.Binding binding, List<E2aPageItem> items,
        long captureHighWatermark) {
        return require(binding, items, captureHighWatermark, null);
    }

    public List<E2aPageItem> require(SourceEvidenceIntakePageObjects.Binding binding, List<E2aPageItem> items,
        long captureHighWatermark, UUID existingSetId) {
        return require(binding, items, captureHighWatermark, existingSetId, 0);
    }

    public List<E2aPageItem> require(SourceEvidenceIntakePageObjects.Binding binding, List<E2aPageItem> items,
        long captureHighWatermark, UUID existingSetId, long auditThrough) {
        return requireTrusted(binding, items, captureHighWatermark, existingSetId, auditThrough).ownScans();
    }

    public TrustedFacts requireTrusted(SourceEvidenceIntakePageObjects.Binding binding, List<E2aPageItem> items,
        long captureHighWatermark, UUID existingSetId, long auditThrough) {
        var hashes = jdbc.queryForList("SELECT payload_sha256 FROM submission_payload WHERE submission_id=?",
            String.class, Ids.bytes(binding.submissionId()));
        boolean received = !hashes.isEmpty();
        var payload = received ? payload(binding.submissionId()) : localPayload.require(binding, items, auditThrough);
        long orderScanHighWatermark = SourceEvidenceIntakeOrderScanSequence.require(payload, binding.creatorDeviceId());
        var captures = byId(SubmissionJson.array(payload, "captures"), "id");
        var scans = byCapture(SubmissionJson.array(payload, "scans"));
        require(captures.size() <= captureHighWatermark, "E2A_CAPTURE_FACT_COUNT_CONFLICT");
        var sourceCaptures = new HashSet<UUID>();
        var sourceScans = new HashSet<UUID>();
        var other = new ArrayList<E2aPageItem>();
        var own = new ArrayList<E2aPageItem>();
        for (var item : items) {
            if (item.itemKind().equals("CAPTURE")) {
                if (!captures.containsKey(item.originalRecordId())) other.add(item);
                else {
                    require(sourceCaptures.add(item.originalRecordId()), "E2A_CAPTURE_FACT_DUPLICATE");
                    requireCapture(binding, item, captures, received);
                }
            }
            if (item.itemKind().equals("SCAN")) {
                if (!scans.containsKey(item.captureId())) other.add(item);
                else {
                    require(sourceScans.add(item.captureId()), "E2A_SCAN_FACT_DUPLICATE");
                    requireScan(binding, item, captures, scans, received);
                    own.add(item);
                }
            }
        }
        require(sourceCaptures.size() == captures.size(), "E2A_CAPTURE_FACT_COUNT_CONFLICT");
        require(sourceScans.size() == scans.size(), "E2A_SCAN_FACT_COUNT_CONFLICT");
        new SourceEvidenceIntakeOtherFacts(jdbc, this::payload, unsubmitted).require(binding, other, auditThrough);
        own.stream().filter(item -> "ACTIVE".equals(item.recordStatus()))
            .sorted(Comparator.comparing(item -> item.baleId().toString()))
            .forEach(item -> requireStock(binding, item, existingSetId));
        return new TrustedFacts(List.copyOf(own), orderScanHighWatermark);
    }

    private JsonNode payload(UUID submissionId) {
        var hashes = jdbc.queryForList("SELECT payload_sha256 FROM submission_payload WHERE submission_id=?",
            String.class, Ids.bytes(submissionId));
        if (hashes.size() != 1) throw conflict("E2A_SOURCE_PAYLOAD_UNAVAILABLE");
        try {
            var body=SubmissionJson.parse(files.read(hashes.getFirst()));
            require(SubmissionJson.sha(body).equals(hashes.getFirst()),"E2A_SOURCE_PAYLOAD_UNAVAILABLE");
            return body;
        } catch (RuntimeException failure) {
            throw conflict("E2A_SOURCE_PAYLOAD_UNAVAILABLE");
        }
    }

    private void requireCapture(SourceEvidenceIntakePageObjects.Binding binding, E2aPageItem item,
        Map<UUID, JsonNode> captures, boolean received) {
        var capture = captures.get(item.originalRecordId());
        require(capture != null, "E2A_CAPTURE_FACT_MISSING");
        require(SubmissionJson.uuid(capture, "originDeviceId").equals(binding.creatorDeviceId())
            && SubmissionJson.number(capture, "deviceCaptureSeq") == item.originalDeviceSeq()
            && SubmissionJson.sha(capture).equals(item.recordSha256())
            && SubmissionJson.text(capture, "state").equals(item.recordStatus()), "E2A_CAPTURE_FACT_CONFLICT");
        if (!received) return;
        SourceEvidenceIntakeStoredCaptures.require(jdbc,binding,item);
    }

    private void requireScan(SourceEvidenceIntakePageObjects.Binding binding, E2aPageItem item,
        Map<UUID, JsonNode> captures, Map<UUID, JsonNode> scans, boolean received) {
        require(item.originalRecordId().equals(item.captureId()), "E2A_SCAN_RECORD_ID_CONFLICT");
        var scan = scans.get(item.captureId());
        require(scan != null, "E2A_SCAN_FACT_MISSING");
        var capture = captures.get(item.captureId());
        require(capture != null, "E2A_SCAN_CAPTURE_MISSING");
        require(SubmissionJson.uuid(capture, "originDeviceId").equals(binding.creatorDeviceId())
            && SubmissionJson.number(scan, "localSeq") == item.originalDeviceSeq()
            && SubmissionJson.sha(scan).equals(item.recordSha256())
            && SubmissionJson.uuid(scan, "captureId").equals(item.captureId())
            && SubmissionJson.uuid(scan, "baleId").equals(item.baleId())
            && SubmissionJson.number(scan, "expectedDispatchCycle") == item.originalCycle()
            && SubmissionJson.text(scan, "status").equals(item.recordStatus()), "E2A_SCAN_FACT_CONFLICT");
        require(Set.of("ACTIVE", "VOID").contains(item.recordStatus()), "E2A_SCAN_STATUS_CONFLICT");
        requireCapture(binding, captureItem(capture), captures, received);
    }

    private E2aPageItem captureItem(JsonNode capture) {
        return new E2aPageItem("CAPTURE", SubmissionJson.uuid(capture, "id"), "CAPTURE_RECORDS",
            SubmissionJson.uuid(capture, "id"), SubmissionJson.sha(capture),
            SubmissionJson.canonical(capture).getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
            SubmissionJson.number(capture, "deviceCaptureSeq"), SubmissionJson.sha(capture),
            SubmissionJson.text(capture, "state"), null, null, null);
    }

    private void requireStock(SourceEvidenceIntakePageObjects.Binding binding, E2aPageItem item, UUID existingSetId) {
        var rows = jdbc.query(STOCK, (rs, number) -> new StockRow(rs.getString(1), rs.getLong(2),
            rs.getBoolean(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getLong(7),
            rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11)),
            Ids.bytes(item.baleId()));
        require(rows.size() == 1, "E2A_BALE_FACT_MISSING");
        var stock = rows.getFirst();
        require(UUID.fromString(stock.warehouseId()).equals(binding.warehouseId())
            && stock.dispatchCycle() == item.originalCycle(), "E2A_BALE_CYCLE_CONFLICT");
        require(stock.present() && "NOT_DEDUCTED".equals(stock.bookStatus()) && stock.blockedReason() == null,
            "E2A_BALE_STATE_CONFLICT");
        if (stock.state() != null) {
            boolean sameSource = stock.ownerCycle() == item.originalCycle()
                && binding.scopeId().toString().equals(stock.sourceScopeId())
                && binding.warehouseId().toString().equals(stock.sourceWarehouseId());
            boolean review = "REVIEW_RESERVED".equals(stock.state()) && stock.owner() != null
                && binding.submissionId().toString().equals(stock.owner());
            boolean reserved = "SOURCE_RESERVED".equals(stock.state()) && existingSetId != null
                && existingSetId.toString().equals(stock.evidenceSetId());
            require(sameSource && (review || reserved), "E2A_BALE_OWNER_CONFLICT");
        }
    }

    private static Map<UUID, JsonNode> byId(JsonNode values, String field) {
        var result = new HashMap<UUID, JsonNode>();
        values.forEach(value -> { var id = SubmissionJson.uuid(value, field); require(result.put(id, value) == null, "E2A_SOURCE_FACT_DUPLICATE"); });
        return result;
    }

    private static Map<UUID, JsonNode> byCapture(JsonNode values) {
        var result = new HashMap<UUID, JsonNode>();
        values.forEach(value -> { var id = SubmissionJson.uuid(value, "captureId"); require(result.put(id, value) == null, "E2A_SOURCE_FACT_DUPLICATE"); });
        return result;
    }

    private static void require(boolean valid, String code) { if (!valid) throw conflict(code); }
    private static BusinessException conflict(String code) { return new BusinessException(409, code, "原机逐包业务事实不可核验"); }

    private record StockRow(String warehouseId, long dispatchCycle, boolean present, String bookStatus,
        String blockedReason, String owner, long ownerCycle, String state,
        String sourceScopeId, String sourceWarehouseId, String evidenceSetId) { }

    public record TrustedFacts(List<E2aPageItem> ownScans, long orderScanHighWatermark) {
        public TrustedFacts {
            ownScans = List.copyOf(ownScans);
            if (orderScanHighWatermark < 0) throw new IllegalArgumentException("ORDER_SCAN_HIGH_WATERMARK");
        }
    }
}
