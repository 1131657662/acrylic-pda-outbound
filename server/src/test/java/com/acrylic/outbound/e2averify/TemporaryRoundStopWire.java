package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.temporary.protocol.*;
import com.acrylic.outbound.inventory.ImportFiles;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.KeyPair;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Explicit synthetic device proof, sent through the real E2A protocol. */
final class TemporaryRoundStopWire {
    final ObjectNode body,stopEvent,page,manifest;
    final UUID openKey=UUID.randomUUID(),pageKey=UUID.randomUUID(),stopKey=UUID.randomUUID(),verifyKey=UUID.randomUUID();
    final String manifestId=UUID.randomUUID().toString(),snapshot=UUID.randomUUID().toString();
    final List<Record> records=new ArrayList<>();
    final E2aProtocol.Hashes hashes;
    TemporaryRoundStopWire(ObjectNode body,List<JsonNode> priorAudits) {
        this.body=body.deepCopy();
        var last=array(body,"events").get(array(body,"events").size()-1);
        stopEvent=((ObjectNode)last).deepCopy().put("id",UUID.randomUUID().toString()).put("type","STOP")
            .put("detail",text(body,"submissionId")).put("localSeq",Long.toString(number(last,"localSeq")+1));
        array(body,"captures").forEach(r->add("CAPTURE",r));array(body,"scans").forEach(r->add("SCAN",r));
        var audits=new TreeMap<Long,JsonNode>();priorAudits.forEach(r->audits.put(number(r,"localSeq"),r));
        array(body,"events").forEach(r->{
            var previous=audits.put(number(r,"localSeq"),r);
            org.assertj.core.api.Assertions.assertThat(previous==null || previous.equals(r)).isTrue();
        });
        audits.values().forEach(r->add("AUDIT",r));add("AUDIT",stopEvent);
        page=page();manifest=manifest();hashes=E2aProtocol.manifestHashes(manifest);
    }
    private void add(String kind,JsonNode row) { records.add(new Record(E2aCrossSourceFixture.item(kind,row),row)); }
    ObjectNode open() {
        return object().put("operation","OPEN").put("manifestId",manifestId).put("manifestVersion",1).put("snapshotId",snapshot)
            .put("source","TEMP").put("sourceId",text(body,"authorizationId")).put("submissionId",text(body,"submissionId"))
            .put("workRound",number(body,"workRound"));
    }
    ObjectNode pageRequest() {
        var request=object().put("operation","PAGE").put("openOriginalKey",openKey.toString()).put("pageHash",E2aProtocol.pageHash(page));
        request.set("pageCore",page);return request;
    }
    ObjectNode stop(String warehouse,String operator,KeyPair key) {
        var core=object().put("warehouseId",warehouse).put("sourceId",text(body,"authorizationId"))
            .put("submissionId",text(body,"submissionId")).put("workRound",number(body,"workRound"))
            .put("originalOperatorId",operator).put("freezeDeviceId",text(body,"freezeDeviceId"))
            .put("sourceRecoveryEpoch",number(body,"sourceRecoveryEpoch")).put("stopEventId",text(stopEvent,"id"))
            .put("captureHighWatermark",number(body,"captureHighWatermark")).put("auditThroughSeq",number(stopEvent,"localSeq"))
            .put("stopTransactionVersion",1).put("manifestId",manifestId).put("manifestVersion",1)
            .put("manifestBytesHash",hashes.manifestBytesHash()).put("setHash",hashes.setHash());
        var request=object().put("operation","STOP").put("openOriginalKey",openKey.toString())
            .put("manifestBytesHash",hashes.manifestBytesHash()).put("setHash",hashes.setHash());
        request.set("manifestCore",manifest);request.set("stopProof",E2aProtocol.signStop(core,key.getPrivate(),ImportFiles.sha256(key.getPublic().getEncoded())));
        return request;
    }
    ObjectNode verify() {
        return object().put("operation","VERIFY").put("openOriginalKey",openKey.toString()).put("stopOriginalKey",stopKey.toString())
            .put("manifestId",manifestId).put("manifestVersion",1).put("setHash",hashes.setHash()).put("verificationVersion",1).put("algorithmVersion",1);
    }
    private ObjectNode page() {
        var p=object().put("manifestId",manifestId).put("manifestVersion",1).put("snapshotId",snapshot)
            .put("pageNo",1).put("pageCount",1).put("isFinal",true).put("itemCount",records.size())
            .put("byteCount",records.stream().mapToLong(r->r.item().byteCount()).sum()).putNull("prevPageHash");
        var items=p.putArray("items");records.forEach(r->items.add(item(r.item())));return p;
    }
    private ObjectNode manifest() {
        var m=open();m.remove("operation");m.put("schemaVersion",1).put("pageCount",1).put("entryCount",records.size())
            .put("byteCount",page.path("byteCount").asLong()).put("endPageNo",1);
        m.putArray("pages").addObject().put("pageNo",1).put("pageHash",E2aProtocol.pageHash(page));return m;
    }
    private ObjectNode item(E2aPageItem i) {
        var row=object().put("itemKind",i.itemKind()).put("originalRecordId",i.originalRecordId().toString())
            .put("objectKind",i.objectKind()).put("originalObjectId",i.originalObjectId().toString()).put("itemBytesSha256",i.itemBytesSha256())
            .put("byteCount",i.byteCount()).put("originalDeviceSeq",i.originalDeviceSeq()).put("recordSha256",i.recordSha256())
            .put("recordStatus",i.recordStatus()).putNull("baleId").putNull("originalCycle").putNull("captureId");
        if(i.baleId()!=null)row.put("baleId",i.baleId().toString());if(i.originalCycle()!=null)row.put("originalCycle",i.originalCycle());
        if(i.captureId()!=null)row.put("captureId",i.captureId().toString());return row;
    }
    record Record(E2aPageItem item,JsonNode body) { }
}
