package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionFiles;
import com.acrylic.outbound.temporary.evidence.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real canonical files: previous device facts remain exact, current device pages cover only new facts. */
class HandoverTemporaryPayloadHistoryTest {
    @TempDir Path directory;
    private final UUID old=UUID.randomUUID(), current=UUID.randomUUID(), device=UUID.randomUUID();
    private final HandoverStopStore.Origin origin=new HandoverStopStore.Origin(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
        current,null,device,UUID.randomUUID(),"1",2,
        new HandoverStopStore.Temporary(UUID.randomUUID(),device,UUID.randomUUID()));

    @Test void exactPreviousPrefixDoesNotRequireResigningByCurrentDevice() throws Exception {
        var first=first();var body=body(first);
        assertThat(reader(first,body,false).read(roots(),origin)).isEqualTo(body);
    }
    @Test void exactLocallyFrozenPreviousBodyDoesNotRequireServerPayloadRegistration() throws Exception {
        var first=first();var body=body(first);
        assertThat(reader(first,body,false,true).read(roots(),origin)).isEqualTo(body);
    }
    @ParameterizedTest @ValueSource(strings={"removedCapture","rewrittenScan","wrongPredecessorHash","missingOwnProof","rewrittenOwnCapture"})
    void changedOrUnprovenFactsAreRejected(String mutation) throws Exception {
        var first=first();var body=body(first);boolean missing=false;
        switch(mutation) {
            case "removedCapture" -> array(body,"captures").remove(0);
            case "rewrittenScan" -> ((ObjectNode)array(body,"scans").get(0)).put("detail","changed");
            case "wrongPredecessorHash" -> ((ObjectNode)body.path("temporaryHandover")).put("predecessorPayloadSha256","0".repeat(64));
            case "missingOwnProof" -> missing=true;
            case "rewrittenOwnCapture" -> ((ObjectNode)array(body,"captures").get(1)).put("detail","changed");
            default -> throw new IllegalArgumentException(mutation);
        }
        var reader=reader(first,body,missing);
        assertThatThrownBy(()->reader.read(roots(),origin)).isInstanceOfSatisfying(BusinessException.class,
            error->assertThat(error.code()).isEqualTo("HANDOVER_EVIDENCE_CHANGED"));
    }
    private HandoverTemporaryPayload reader(ObjectNode first,ObjectNode body,boolean missing) throws Exception {
        return reader(first,body,missing,false);
    }
    private HandoverTemporaryPayload reader(ObjectNode first,ObjectNode body,boolean missing,boolean local) throws Exception {
        var files=new SubmissionFiles(directory.toString());String oldHash=files.store(HandoverReceiptCodec.bytes(first));
        String hash=files.store(HandoverReceiptCodec.bytes(body));var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(),eq(String.class),any(Object[].class))).thenAnswer(call->{
            var arguments=(Object[])call.getRawArguments()[2];
            return Arrays.equals((byte[])arguments[0],com.acrylic.outbound.common.Ids.bytes(current))?List.of(hash):local?List.of():List.of(oldHash);
        });
        var pages=mock(SourceEvidenceIntakePageObjects.class);var pageFiles=mock(SourceEvidenceIntakeFiles.class);
        var stored=new SourceEvidenceIntakeFiles.Stored("unused","a".repeat(64),1);
        when(pages.findAfterSourceLock(isNull(),eq(1L))).thenReturn(new SourceEvidenceIntakePageObjects.PageObject(UUID.randomUUID(),UUID.randomUUID(),stored));
        when(pageFiles.readPage(stored)).thenReturn(HandoverReceiptCodec.bytes(page(body(first),missing)));
        var objects=new SourceEvidenceIntakeObjectFiles(directory.toString());
        var bytes=HandoverReceiptCodec.bytes(first);
        var frozen=objects.store(new java.io.ByteArrayInputStream(bytes),bytes.length,sha(first));
        when(jdbc.query(contains("temporary_handover_activation_ticket"),any(org.springframework.jdbc.core.RowMapper.class),any(Object[].class)))
            .thenReturn(List.of(frozen));
        return new HandoverTemporaryPayload(jdbc,files,objects,pages,pageFiles,null,null);
    }
    private ObjectNode first() {
        var body=object().put("submissionId",old.toString());
        body.putArray("captures").addObject().put("id",old.toString()).put("detail","original");
        body.putArray("scans").addObject().put("captureId",old.toString()).put("detail","original");
        body.putArray("events");return body;
    }
    private ObjectNode body(ObjectNode first) {
        var body=first.deepCopy().put("submissionId",current.toString()).put("schemaVersion",2)
            .put("authorizationId",origin.temporary().authorization().toString()).put("freezeDeviceId",device.toString())
            .put("workRound","2").put("sourceRecoveryEpoch","1").putNull("assignmentId").putNull("continuationId");
        body.putObject("source").put("kind","TEMP").put("creatorDeviceId",device.toString()).put("clientOrderId",origin.temporary().client().toString());
        body.putObject("temporaryHandover").put("version",1).put("ticketId",UUID.randomUUID().toString())
            .put("caseId",UUID.randomUUID().toString()).put("predecessorSubmissionId",old.toString())
            .put("predecessorPayloadSha256",sha(first)).put("sourcePrefixSha256","a".repeat(64));
        array(body,"captures").addObject().put("id",current.toString()).put("detail","own");
        array(body,"scans").addObject().put("captureId",current.toString()).put("detail","own");return body;
    }
    private ObjectNode page(ObjectNode body,boolean missing) {
        var page=object();var items=page.putArray("items");
        if(missing)return page.put("byteCount",0);
        for(String kind:List.of("CAPTURE","SCAN")) {
            var record=array(body,"CAPTURE".equals(kind)?"captures":"scans").get(1);
            var item=items.addObject().put("itemKind",kind).put("originalRecordId",current.toString()).put("objectKind","CAPTURE_RECORDS")
                .put("originalObjectId",UUID.randomUUID().toString()).put("itemBytesSha256","a".repeat(64)).put("byteCount",1)
                .put("originalDeviceSeq",1).put("recordSha256",sha(record)).put("recordStatus","COMMITTED")
                .putNull("baleId").putNull("originalCycle").putNull("captureId");
            if("SCAN".equals(kind))item.put("baleId",UUID.randomUUID().toString()).put("originalCycle",1).put("captureId",current.toString());
        }
        return page.put("byteCount",2);
    }
    private HandoverTemporaryRoots.Verified roots() {return new HandoverTemporaryRoots.Verified(null,object().put("pageCount",1));}
}
