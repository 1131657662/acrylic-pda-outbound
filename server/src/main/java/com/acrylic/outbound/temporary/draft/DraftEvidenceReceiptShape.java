package com.acrylic.outbound.temporary.draft;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;

/** Binds signed transport receipts to the immutable original request and database descriptors. */
final class DraftEvidenceReceiptShape {
    private DraftEvidenceReceiptShape() { }
    static void check(JsonNode value,DraftEvidenceStore.Header header,UUID key,String operation,byte[] request,DraftEvidenceStore store) {
        var expected=SubmissionJson.object().put("receiptVersion",1).put("operationKind",operation).put("originalKey",key.toString())
            .put("evidenceId",header.evidence().toString()).put("clientOrderId",header.client().toString())
            .put("warehouseId",header.warehouse().toString()).put("creatorDeviceId",header.device().toString()).put("originalOperatorId",header.operator().toString())
            .put("rootSha256",header.root()).put("requestSha256",DraftEvidenceWire.sha(request)).put("recordedAt",value.path("recordedAt").textValue())
            .put("status",operation.equals("VERIFY")?"ORIGINALS_STORED":"RECEIVED").put("businessFactsStatus","NOT_EVALUATED")
            .put("localDeletionAllowed",false).put("releaseAllowed",false).put("mayResumeScanning",false).put("writeConfirmationAvailable",false);
        Instant.parse(SubmissionJson.text(value,"recordedAt"));
        expected.set("detail",detail(header,key,operation,request,value.path("detail"),store));
        DraftEvidenceWire.require(Arrays.equals(DraftEvidenceWire.bytes(value),DraftEvidenceWire.bytes(expected)));
    }
    private static JsonNode detail(DraftEvidenceStore.Header header,UUID key,String operation,byte[] request,JsonNode detail,DraftEvidenceStore store) {
        return switch(operation) {
            case "OPEN" -> {
                DraftEvidenceWire.require(key.equals(header.openKey()) && Arrays.equals(request,header.openBytes()));
                yield SubmissionJson.object();
            }
            case "ITEM" -> item(header,request,store);
            case "VERIFY" -> {
                var original=SubmissionJson.object().put("evidenceId",header.evidence().toString()).put("rootSha256",header.root());
                DraftEvidenceWire.require(Arrays.equals(request,DraftEvidenceWire.bytes(original)) && store.completedBy(header.evidence(),header.operator(),key));
                DraftEvidenceWire.require(detail.size()==5 && detail.path("recordCount").isIntegralNumber() && detail.path("recordCount").canConvertToInt() && detail.path("recordCount").intValue()==header.count()
                    && detail.path("byteCount").isIntegralNumber() && detail.path("byteCount").longValue()==header.bytes());
                for(String field:List.of("ownCaptureCount","otherCaptureCount","archiveCount"))
                    DraftEvidenceWire.require(detail.path(field).isIntegralNumber() && detail.path(field).longValue()>=0 && detail.path(field).longValue()<=header.count());
                yield detail;
            }
            default -> throw DraftEvidenceWire.invalid();
        };
    }
    private static JsonNode item(DraftEvidenceStore.Header header,byte[] request,DraftEvidenceStore store) {
        var descriptor=SubmissionJson.parse(request);
        DraftEvidenceWire.require(descriptor.path("ordinal").isIntegralNumber());
        var item=store.item(header.evidence(),descriptor.path("ordinal").longValue());
        DraftEvidenceWire.require(item!=null && Arrays.equals(request,item.descriptor(header.evidence())));
        return SubmissionJson.object().put("ordinal",item.ordinal()).put("bytesSha256",item.stored().sha256());
    }
}
