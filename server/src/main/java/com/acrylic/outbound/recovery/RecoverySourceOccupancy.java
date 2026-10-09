package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Original reservations only; a restored database still needs its own locked stock recheck. */
public final class RecoverySourceOccupancy {
    private static final String ROW_FIELDS = "baleId barcode dispatchCycle orderScanSeq captureId ownerState ownerSubmissionId "
        + "ownerScopeId ownerWarehouseId ownerOrderId sourceEvidenceSetId continuationId recoveryPackageId dispositionId "
        + "dispositionEvidenceId pcWorkId pcDispositionId temporaryContinuationId temporaryHandoverActivationId "
        + "upstreamPresent bookStatus blockedReason";
    private final RecoverySourceTerminal terminals;
    private final RecoverySourceAuthority credentials;

    public RecoverySourceOccupancy(RecoveryReceiptKeys keys) {
        terminals = new RecoverySourceTerminal(new RecoveryReceiptVerifier(keys)); credentials = new RecoverySourceAuthority(keys);
    }

    public Proof verify(RecoveryPinnedSource source) {
        require(source != null, "RECOVERY_SOURCE_IDENTITY");
        try {
            require(source.identity().workRound() == 1, "RECOVERY_SOURCE_OCCUPANCY_ROUND_UNSUPPORTED");
            var payload = SubmissionJson.parse(source.original("payload"));
            requireInitial(source, payload);
            JsonNode value = SubmissionJson.parse(source.original("occupancy"));
            fields(value, "formatVersion source payloadSha256 sourceEvidenceSetId rows"); version(value);
            require(source.identity().equals(RecoverySourceIdentity.read(value.path("source"))), "RECOVERY_SOURCE_OCCUPANCY_SOURCE");
            require(text(value, "payloadSha256").equals(SubmissionJson.sha(payload)), "RECOVERY_SOURCE_OCCUPANCY_PAYLOAD");
            UUID evidence = nullable(value, "sourceEvidenceSetId");
            var rows = new ArrayList<Row>(); var bales = new HashSet<UUID>(); var captures = new HashSet<UUID>();
            for (JsonNode row : SubmissionJson.array(value, "rows")) {
                fields(row, ROW_FIELDS); Row parsed = readRow(row, source.identity(), evidence);
                require(bales.add(parsed.baleId()) && captures.add(parsed.captureId()), "RECOVERY_SOURCE_OCCUPANCY_DUPLICATE");
                rows.add(parsed);
            }
            checkScans(payload, rows);
            return new Proof(source.identity(), text(value, "payloadSha256"), evidence, List.copyOf(rows));
        } catch (RecoveryControlException refusal) { throw refusal; }
        catch (RuntimeException invalid) { throw new RecoveryControlException("RECOVERY_SOURCE_OCCUPANCY_INVALID", invalid); }
    }

    private void requireInitial(RecoveryPinnedSource source, JsonNode payload) {
        credentials.verify(source);
        for (String field : List.of("continuationId", "handoverSessionId", "recoveryPackageId", "handoverId"))
            require(!payload.hasNonNull(field), "RECOVERY_SOURCE_OCCUPANCY_CHAIN_UNSUPPORTED");
        require(!payload.has("dispositions") || (payload.path("dispositions").isArray() && payload.path("dispositions").isEmpty()),
            "RECOVERY_SOURCE_OCCUPANCY_CHAIN_UNSUPPORTED");
        var terminal = terminals.verify(source);
        require("ABORTED".equals(text(terminal, "status")), "RECOVERY_SOURCE_OCCUPANCY_TERMINAL_UNSUPPORTED");
        require(terminal.hasNonNull("payloadSha256") || terminal.hasNonNull("localFrozenPayloadSha256"),
            "RECOVERY_SOURCE_OCCUPANCY_TERMINAL_PAYLOAD");
    }

    private Row readRow(JsonNode row, RecoverySourceIdentity source, UUID evidence) {
        String state = text(row, "ownerState");
        require(Set.of("REVIEW_RESERVED", "SOURCE_RESERVED").contains(state), "RECOVERY_SOURCE_OCCUPANCY_OWNER_STATE");
        require(revision(row, "dispatchCycle") > 0 && revision(row, "orderScanSeq") > 0, "RECOVERY_SOURCE_OCCUPANCY_SEQUENCE");
        require(text(row, "barcode").matches("[A-Z0-9]{11}"), "RECOVERY_SOURCE_OCCUPANCY_BARCODE");
        require(row.path("upstreamPresent").isBoolean() && row.path("upstreamPresent").booleanValue()
            && "NOT_DEDUCTED".equals(text(row, "bookStatus")) && row.path("blockedReason").isNull(),
            "RECOVERY_SOURCE_OCCUPANCY_UNAVAILABLE");
        require(allNull(row, "continuationId", "recoveryPackageId", "dispositionId", "dispositionEvidenceId", "pcWorkId",
            "pcDispositionId", "temporaryContinuationId", "temporaryHandoverActivationId"), "RECOVERY_SOURCE_OCCUPANCY_CHAIN_UNSUPPORTED");
        requireOwner(row, source, evidence);
        return new Row(uuid(row, "baleId"), text(row, "barcode"), revision(row, "dispatchCycle"), revision(row, "orderScanSeq"),
            uuid(row, "captureId"), state, nullable(row, "ownerSubmissionId"), nullable(row, "ownerScopeId"),
            nullable(row, "ownerWarehouseId"), nullable(row, "ownerOrderId"), nullable(row, "sourceEvidenceSetId"));
    }

    private void requireOwner(JsonNode row, RecoverySourceIdentity source, UUID evidence) {
        boolean temporary = source.sourceKind().equals("TEMP");
        require(Objects.equals(nullable(row, "ownerScopeId"), temporary ? source.scopeId() : null)
            && Objects.equals(nullable(row, "ownerWarehouseId"), temporary ? source.warehouseId() : null),
            "RECOVERY_SOURCE_OCCUPANCY_OWNER");
        if (text(row, "ownerState").equals("REVIEW_RESERVED")) {
            require(source.submissionId().equals(nullable(row, "ownerSubmissionId"))
                && Objects.equals(source.canonicalOrderId(), nullable(row, "ownerOrderId"))
                && evidence == null && row.path("sourceEvidenceSetId").isNull(), "RECOVERY_SOURCE_OCCUPANCY_OWNER");
        } else require(temporary && evidence != null && evidence.equals(nullable(row, "sourceEvidenceSetId"))
            && allNull(row, "ownerSubmissionId", "ownerOrderId"), "RECOVERY_SOURCE_OCCUPANCY_OWNER");
    }

    private void checkScans(JsonNode payload, List<Row> rows) {
        var captures = new HashMap<UUID, JsonNode>();
        for (var capture : SubmissionJson.array(payload, "captures"))
            require(captures.put(uuid(capture, "id"), capture) == null, "RECOVERY_SOURCE_OCCUPANCY_CAPTURE_DUPLICATE");
        var seen = new HashSet<UUID>(); long previous = 0; int index = 0;
        for (var scan : SubmissionJson.array(payload, "scans")) {
            UUID captureId = uuid(scan, "captureId"); var capture = captures.get(captureId);
            require(capture != null && seen.add(captureId), "RECOVERY_SOURCE_OCCUPANCY_CAPTURE_MISSING");
            require(revision(scan, "orderScanSeq") > previous, "RECOVERY_SOURCE_OCCUPANCY_SCAN_ORDER");
            previous = revision(scan, "orderScanSeq"); checkCapture(scan, capture);
            String status = text(scan, "status");
            require(Set.of("ACTIVE", "VOID").contains(status), "RECOVERY_SOURCE_OCCUPANCY_SCAN_STATUS");
            if (status.equals("VOID")) continue;
            require(index < rows.size(), "RECOVERY_SOURCE_OCCUPANCY_ROW_SET");
            Row row = rows.get(index++);
            require(row.captureId().equals(captureId) && row.baleId().equals(uuid(scan, "baleId"))
                && row.barcode().equals(text(scan, "barcode")) && row.orderScanSeq() == previous
                && row.dispatchCycle() == revision(scan, "expectedDispatchCycle"), "RECOVERY_SOURCE_OCCUPANCY_SCAN_MISMATCH");
        }
        require(index == rows.size(), "RECOVERY_SOURCE_OCCUPANCY_ROW_SET");
    }

    private void checkCapture(JsonNode scan, JsonNode capture) {
        require("COMMITTED".equals(text(capture, "state")) && text(scan, "barcode").equals(text(capture, "barcode"))
            && revision(scan, "orderScanSeq") == revision(capture, "orderScanSeq")
            && revision(scan, "expectedDispatchCycle") == revision(capture, "expectedDispatchCycle"),
            "RECOVERY_SOURCE_OCCUPANCY_CAPTURE_MISMATCH");
    }

    private static boolean allNull(JsonNode value, String... keys) {
        for (String key : keys) if (!value.path(key).isNull()) return false;
        return true;
    }
    private static UUID nullable(JsonNode value, String key) { return value.path(key).isNull() ? null : uuid(value, key); }

    public record Proof(RecoverySourceIdentity identity, String payloadSha256, UUID sourceEvidenceSetId, List<Row> rows) { }
    public record Row(UUID baleId, String barcode, long dispatchCycle, long orderScanSeq, UUID captureId, String ownerState,
            UUID ownerSubmissionId, UUID ownerScopeId, UUID ownerWarehouseId, UUID ownerOrderId, UUID sourceEvidenceSetId) { }
}
