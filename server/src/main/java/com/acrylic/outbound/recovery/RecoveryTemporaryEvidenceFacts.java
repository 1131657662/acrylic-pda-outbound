package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static com.acrylic.outbound.recovery.RecoveryTemporaryEvidenceColumns.*;

/** Raw rows must reproduce the verified original E2A descriptors and SOURCE_RESERVED ownership exactly. */
final class RecoveryTemporaryEvidenceFacts {
    private RecoveryTemporaryEvidenceFacts() { }

    static void verify(JsonNode rows,RecoverySourceIdentity source,RecoveryOriginalEvidence.Request evidence,
            RecoverySourceOccupancy.Proof occupancy,Instant observed) {
        var objects=new HashMap<String,JsonNode>();
        for(var table:RecoveryTemporaryEvidenceTable.values()) {
            var values=SubmissionJson.array(rows,table.table); require(values.size()<=65536,"RECOVERY_EVIDENCE_ROW_LIMIT");
            var primary=new HashSet<String>();
            for(var row:values) {
                values(row,table,table.columns);
                String key=table.primary.stream().map(field -> row.path(field).toString()).collect(java.util.stream.Collectors.joining("/"));
                require(primary.add(key),"RECOVERY_EVIDENCE_ROW_DUPLICATE");
                for(String field:table.times) require(!Instant.parse(text(row,field)).isAfter(observed),"RECOVERY_EVIDENCE_ROW_TIME");
                if(table!=RecoveryTemporaryEvidenceTable.RESERVATION) {
                    equalsId(row,"warehouse_id",source.warehouseId()); equalsId(row,"scope_id",source.scopeId());
                    equalsId(row,"submission_id",source.submissionId()); require(revision(row,"work_round")==1,"RECOVERY_EVIDENCE_ROW_SOURCE");
                }
            }
        }
        for(var object:rows.path("source_evidence_object")) {
            equalsId(object,"origin_device_id",source.deviceId()); equalsId(object,"uploaded_by",source.operatorId());
            require(objects.put(text(object,"id"),object)==null,"RECOVERY_EVIDENCE_ROW_DUPLICATE");
        }
        set(rows,source,evidence,occupancy,objects); items(rows,evidence,objects); reservations(rows,occupancy);
    }

    private static void set(JsonNode rows,RecoverySourceIdentity source,RecoveryOriginalEvidence.Request evidence,
            RecoverySourceOccupancy.Proof occupancy,Map<String,JsonNode> objects) {
        var sets=SubmissionJson.array(rows,"source_evidence_set"); require(sets.size()==1,"RECOVERY_EVIDENCE_ROW_SET");
        var set=sets.get(0); equalsId(set,"id",occupancy.sourceEvidenceSetId()); var expected=RecoveryUnsubmittedEvidence.expected(evidence);
        require(Arrays.equals(Base64.getDecoder().decode(text(set,"manifest_bytes")),expected.manifestBytes())
            && text(set,"set_sha256").equals(expected.proof().setHash()) && revision(set,"item_count")==expected.proof().itemCount()
            && revision(set,"capture_high_watermark")==source.captureHighWatermark()
            && revision(set,"audit_through_seq")==source.auditThroughSeq()+1 && revision(set,"order_scan_high_watermark")==source.orderScanHighWatermark(),
            "RECOVERY_EVIDENCE_ROW_SET");
        var stop=objects.get(text(set,"stop_object_id"));
        require(stop!=null && text(stop,"kind").equals("STOP_PROOF")
            && text(stop,"bytes_sha256").equals(sha256(expected.stopProofBytes())) && revision(stop,"byte_count")==expected.stopProofBytes().length,
            "RECOVERY_EVIDENCE_ROW_STOP");
        equalsId(stop,"original_object_id",com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeStopObjects.originalId(
            evidence.openOriginalKey(),expected.proof().stopEventId()));
    }

    private static void items(JsonNode rows,RecoveryOriginalEvidence.Request evidence,Map<String,JsonNode> objects) {
        var expected=RecoveryUnsubmittedEvidence.expected(evidence); var actual=SubmissionJson.object().putArray("items");
        var seen=new HashSet<String>(); String set=text(rows.path("source_evidence_set").get(0),"id");
        for(var item:rows.path("source_evidence_item")) {
            require(text(item,"set_id").equals(set),"RECOVERY_EVIDENCE_ROW_ITEM"); var object=objects.get(text(item,"object_id"));
            require(object!=null && !text(object,"kind").equals("STOP_PROOF"),"RECOVERY_EVIDENCE_ROW_ITEM"); seen.add(text(object,"id"));
            var row=actual.addObject().put("itemKind",text(item,"item_kind")).put("originalRecordId",id(item,"original_record_id"))
                .put("originalObjectId",id(object,"original_object_id")).put("objectKind",text(object,"kind"))
                .put("originalDeviceSeq",text(item,"original_device_seq")).put("recordSha256",text(item,"record_sha256"))
                .put("recordStatus",text(item,"record_status")).put("objectSha256",text(object,"bytes_sha256")).put("objectByteCount",text(object,"byte_count"));
            row.put("baleId",id(item,"bale_id")).put("captureId",id(item,"capture_id")); row.set("originalCycle",item.path("original_cycle"));
        }
        var sorted=new ArrayList<JsonNode>(); actual.forEach(sorted::add);
        sorted.sort(Comparator.comparing(row -> text(row,"itemKind")+"/"+text(row,"originalRecordId")));
        actual.removeAll(); sorted.forEach(actual::add);
        require(SubmissionJson.sha(actual).equals(expected.itemsSha256()),"RECOVERY_EVIDENCE_ROW_ITEMS");
        pages(evidence,objects,seen);
        seen.add(text(rows.path("source_evidence_set").get(0),"stop_object_id"));
        require(objects.keySet().equals(seen),"RECOVERY_EVIDENCE_ROW_OBJECT_SET");
    }

    private static void pages(RecoveryOriginalEvidence.Request evidence,Map<String,JsonNode> objects,Set<String> seen) {
        int number=0;
        for(byte[] command:evidence.bundle().pageBytes()) {
            byte[] bytes=SubmissionJson.canonical(SubmissionJson.parse(command).path("pageCore")).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            UUID original=com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakePageObjects.originalId(evidence.openOriginalKey(),++number);
            var matches=new ArrayList<JsonNode>();
            for(var object:objects.values()) if(text(object,"kind").equals("MANIFEST_PAGE")
                && uuidBytes(original).equals(text(object,"original_object_id"))) matches.add(object);
            require(matches.size()==1,"RECOVERY_EVIDENCE_ROW_PAGE"); var object=matches.getFirst();
            require(text(object,"bytes_sha256").equals(sha256(bytes)) && revision(object,"byte_count")==bytes.length,"RECOVERY_EVIDENCE_ROW_PAGE");
            seen.add(text(object,"id"));
        }
    }

    private static void reservations(JsonNode rows,RecoverySourceOccupancy.Proof occupancy) {
        var values=SubmissionJson.array(rows,"bale_commitment"); require(values.size()==occupancy.rows().size(),"RECOVERY_EVIDENCE_ROW_OWNER");
        for(var original:occupancy.rows()) {
            var matches=new ArrayList<JsonNode>();
            for(var row:values) if(uuidBytes(original.baleId()).equals(text(row,"bale_id"))) matches.add(row);
            require(matches.size()==1,"RECOVERY_EVIDENCE_ROW_OWNER"); var row=matches.getFirst();
            require(text(row,"state").equals("SOURCE_RESERVED") && revision(row,"dispatch_cycle")==original.dispatchCycle(),"RECOVERY_EVIDENCE_ROW_OWNER");
            equalsId(row,"source_scope_id",original.ownerScopeId()); equalsId(row,"source_warehouse_id",original.ownerWarehouseId());
            equalsId(row,"source_evidence_set_id",original.sourceEvidenceSetId());
            for(String field:List.of("order_id","submission_id","recovery_package_id","continuation_id","disposition_evidence_id",
                    "pc_work_id","pc_disposition_id","disposition_id","temporary_continuation_id","temporary_handover_activation_id"))
                require(row.path(field).isNull(),"RECOVERY_EVIDENCE_ROW_OWNER");
        }
    }

    private static String id(JsonNode row,String field) {
        if(row.path(field).isNull()) return null;
        var buffer=java.nio.ByteBuffer.wrap(Base64.getDecoder().decode(text(row,field)));
        return new UUID(buffer.getLong(),buffer.getLong()).toString();
    }
}
