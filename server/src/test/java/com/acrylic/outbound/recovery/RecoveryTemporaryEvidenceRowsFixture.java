package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.evidence.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static com.acrylic.outbound.recovery.RecoveryTemporaryEvidenceColumns.*;

/** Complete synthetic native row inventory; no database facts or trusted production identity are claimed. */
final class RecoveryTemporaryEvidenceRowsFixture {
    final RecoveryUnsubmittedOccupancyFixture source;
    final ObjectNode rows=SubmissionJson.object();
    final Path storage;
    final Instant observed;

    RecoveryTemporaryEvidenceRowsFixture() throws Exception {
        this(new RecoveryUnsubmittedOccupancyFixture(),null,Instant.parse("2026-09-20T03:00:00Z"));
    }
    RecoveryTemporaryEvidenceRowsFixture(RecoveryUnsubmittedOccupancyFixture original,Path files,Instant time) throws Exception {
        source=original; observed=time;
        storage=files==null?Files.createDirectory(source.initial.state.authority.source.control.restoreRoot.resolve("storage")):files;
        for(var table:RecoveryTemporaryEvidenceTable.values()) rows.putArray(table.table);
        var evidence=source.evidence; var expected=RecoveryUnsubmittedEvidence.expected(evidence.request());
        UUID stopId=object("STOP_PROOF",SourceEvidenceIntakeStopObjects.originalId(evidence.openKey,expected.proof().stopEventId()),expected.stopProofBytes());
        var set=row(RecoveryTemporaryEvidenceTable.SET); set.put("id",uuidBytes(source.setId)).put("stop_object_id",uuidBytes(stopId))
            .put("manifest_bytes",Base64.getEncoder().encodeToString(expected.manifestBytes())).put("set_sha256",expected.proof().setHash())
            .put("capture_high_watermark",Long.toString(evidence.identity.captureHighWatermark()))
            .put("audit_through_seq",Long.toString(evidence.identity.auditThroughSeq())).put("order_scan_high_watermark",Long.toString(evidence.identity.orderScanHighWatermark()))
            .put("item_count",Integer.toString(expected.proof().itemCount()));
        for(var entry:evidence.entries) item(entry);
        int number=0;
        for(byte[] command:evidence.request().bundle().pageBytes()) object("MANIFEST_PAGE",SourceEvidenceIntakePageObjects.originalId(evidence.openKey,++number),
            SubmissionJson.canonical(SubmissionJson.parse(command).path("pageCore")).getBytes(StandardCharsets.UTF_8));
        var owner=source.initial.state.row(); var reservation=row(RecoveryTemporaryEvidenceTable.RESERVATION);
        reservation.put("bale_id",uuidBytes(uuid(owner,"baleId"))).put("dispatch_cycle",text(owner,"dispatchCycle")).put("state","SOURCE_RESERVED")
            .put("source_scope_id",uuidBytes(source.initial.expected().scopeId())).put("source_evidence_set_id",uuidBytes(source.setId))
            .put("source_warehouse_id",uuidBytes(source.initial.expected().warehouseId()));
    }

    private UUID object(String kind,UUID original,byte[] bytes) throws Exception {
        var value=row(RecoveryTemporaryEvidenceTable.OBJECT); UUID id=UUID.randomUUID(); String hash=sha256(bytes);
        value.put("id",uuidBytes(id)).put("kind",kind).put("original_object_id",uuidBytes(original)).put("bytes_sha256",hash)
            .put("byte_count",Integer.toString(bytes.length)).put("file_key",hash+".bin")
            .put("origin_device_id",uuidBytes(source.initial.expected().deviceId())).put("uploaded_by",uuidBytes(source.initial.expected().operatorId()));
        if(!Files.exists(storage.resolve(hash+".bin"))) Files.write(storage.resolve(hash+".bin"),bytes); return id;
    }

    private void item(ObjectNode entry) throws Exception {
        UUID object=object(text(entry,"objectKind"),uuid(entry,"originalObjectId"),source.evidence.originals.get(text(entry,"itemBytesSha256")));
        var value=row(RecoveryTemporaryEvidenceTable.ITEM).put("set_id",uuidBytes(source.setId)).put("item_kind",text(entry,"itemKind"))
            .put("original_record_id",uuidBytes(uuid(entry,"originalRecordId"))).put("object_id",uuidBytes(object))
            .put("original_device_seq",entry.path("originalDeviceSeq").asText()).put("record_sha256",text(entry,"recordSha256"))
            .put("record_status",text(entry,"recordStatus"));
        for(String field:List.of("baleId","captureId")) if(entry.hasNonNull(field)) value.put(field.equals("baleId")?"bale_id":"capture_id",uuidBytes(uuid(entry,field)));
        if(entry.hasNonNull("originalCycle")) value.put("original_cycle",entry.path("originalCycle").asText());
    }

    private ObjectNode row(RecoveryTemporaryEvidenceTable table) {
        var value=SubmissionJson.object(); for(String field:table.columns) value.putNull(field);
        if(table!=RecoveryTemporaryEvidenceTable.RESERVATION) {
            var id=source.initial.expected(); value.put("warehouse_id",uuidBytes(id.warehouseId())).put("scope_id",uuidBytes(id.scopeId()))
                .put("submission_id",uuidBytes(id.submissionId())).put("work_round","1");
        }
        for(String field:table.times) value.put(field,observed.minusMillis(1).toString());
        ((com.fasterxml.jackson.databind.node.ArrayNode)rows.path(table.table)).add(value); return value;
    }
    RecoverySourceOccupancy.Proof occupancy() { return source.reader().read(source.request(),source.initial.source(),source.evidence.request()).occupancy(); }
    void verify() { RecoveryTemporaryEvidenceFacts.verify(rows,source.initial.expected(),source.evidence.request(),occupancy(),observed); }
    ObjectNode first(String table) { return (ObjectNode)rows.path(table).get(0); }
}
