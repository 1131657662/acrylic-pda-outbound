package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Durable transport only. Finalization uses the same locked handover acceptance transaction. */
@Service
@ConditionalOnProperty(prefix="outbound.temporary.handover-intake",name="enabled",havingValue="true")
@Transactional(isolation=Isolation.READ_COMMITTED)
public class TemporaryHandoverChunks {
    private final TemporaryHandoverIntakeAccess access;private final TemporaryHandoverRegistration registration;private final TemporaryHandoverIntakeFacts facts;
    private final SubmissionStore submissions;private final SubmissionFiles files;private final JdbcTemplate jdbc;private final TemporaryHandoverIntake intake;
    public TemporaryHandoverChunks(TemporaryHandoverIntakeAccess access,TemporaryHandoverRegistration registration,TemporaryHandoverIntakeFacts facts,
        SubmissionStore submissions,SubmissionFiles files,JdbcTemplate jdbc,TemporaryHandoverIntake intake) {
        this.access=access;this.registration=registration;this.facts=facts;this.submissions=submissions;
        this.files=files;this.jdbc=jdbc;this.intake=intake;
    }

    public JsonNode start(UUID id,JsonNode body,String epoch) {
        var request=TemporaryHandoverControl.read(body);equal(request.control().id(),id,"分块路径与提交ID不符");
        var manifest=manifest(body);var head=lock(request,epoch);String accepted=submissions.payloadHash(id);
        submissions.requireUploadHash(id,manifest.hash());
        var existing=upload(id);if(existing!=null)equal(existing,manifest,"同批次交接分块清单不可更改");
        if(accepted!=null) {
            equal(accepted,manifest.hash(),"原交接正文已固定");return intake.read(id);
        }
        receiving(head);
        if(existing==null)jdbc.update("INSERT INTO submission_upload VALUES(?,?,?,?)",Ids.bytes(id),manifest.hash(),manifest.bytes(),manifest.count());
        if("REGISTERED".equals(head.status()))submissions.state(id,"RECEIVING",null,null);
        return intake.read(id);
    }

    public JsonNode chunk(UUID id,int index,byte[] bytes,String epoch) {
        var head=lock(existing(id),epoch);receiving(head);var upload=upload(id);
        require(upload!=null && index>=0 && index<upload.count(),"交接分块会话或编号无效");
        int expected=index==upload.count()-1?upload.bytes()-index*262144:262144;
        require(bytes.length==expected,"交接分块长度与清单不一致");
        String hash=files.store(bytes);
        var old=jdbc.queryForList("SELECT sha256 FROM submission_chunk WHERE submission_id=? AND chunk_index=?",String.class,Ids.bytes(id),index);
        if(old.isEmpty())jdbc.update("INSERT INTO submission_chunk VALUES(?,?,?,?,?)",Ids.bytes(id),index,bytes.length,hash,"submissions/"+hash+".json");
        else if(!old.getFirst().equals(hash))throw new BusinessException(409,"IDEMPOTENCY_CONTENT_MISMATCH","同一交接分块内容不可更改");
        return object().put("submissionId",id.toString()).put("chunkIndex",index).put("sha256",hash);
    }

    public JsonNode finish(UUID id,String epoch) {
        var request=existing(id);var head=lock(request,epoch);
        if(submissions.payloadHash(id)!=null)return intake.read(id);
        receiving(head);var upload=upload(id);require(upload!=null,"交接分块会话不存在");
        var body=assemble(id,upload);equal(TemporaryHandoverControl.read(body),request,"交接分块正文与注册来源不符");
        return intake.submit(body,epoch);
    }

    private SubmissionStore.Head lock(TemporaryHandoverControl request,String epoch) {
        var principal=access.require(request,epoch);registration.register(request,principal);
        var head=submissions.required(request.control().id());equal(head.control(),request.control(),"交接注册身份不符");
        if(submissions.payloadHash(head.control().id())==null) {
            var source=facts.read(request);facts.actor(source,principal);
        }
        return head;
    }

    private JsonNode assemble(UUID id,Upload upload) {
        var chunks=jdbc.query("SELECT chunk_index,byte_count,sha256 FROM submission_chunk WHERE submission_id=? ORDER BY chunk_index",
            (rs,n)->new Chunk(rs.getInt(1),rs.getInt(2),rs.getString(3)),Ids.bytes(id));
        require(chunks.size()==upload.count(),"交接分块尚未完整，不可受理");
        var buffer=new java.io.ByteArrayOutputStream(upload.bytes());
        for(int i=0;i<chunks.size();i++) {
            var chunk=chunks.get(i);require(chunk.index()==i,"交接分块编号不连续");byte[] bytes=files.read(chunk.hash());
            int expected=i==upload.count()-1?upload.bytes()-i*262144:262144;
            require(bytes.length==expected && chunk.bytes()==expected && ImportFiles.sha256(bytes).equals(chunk.hash()),"交接分块原件损坏");
            buffer.writeBytes(bytes);
        }
        byte[] content=buffer.toByteArray();require(content.length==upload.bytes() && ImportFiles.sha256(content).equals(upload.hash()),"交接完整正文摘要或长度错误");
        var body=parse(content);equal(sha(body),upload.hash(),"交接上传必须为规范冻结原文");return body;
    }

    private Upload manifest(JsonNode body) {
        var fields=new HashSet<String>();body.fieldNames().forEachRemaining(fields::add);
        equal(fields,Set.of("schemaVersion","submissionId","source","assignmentId","continuationId","authorizationId",
            "freezeDeviceId","workRound","sourceRecoveryEpoch","temporaryHandover","payloadSha256","byteCount","chunkCount"),"交接分块清单字段错误");
        String hash=text(body,"payloadSha256");long size=number(body,"byteCount"),count=number(body,"chunkCount");
        require(hash.matches("[a-f0-9]{64}") && size>0 && size<=33554432 && count==(size+262143)/262144,"交接分块清单无效");
        return new Upload(hash,(int)size,(int)count);
    }
    private TemporaryHandoverControl existing(UUID id) { return new TemporaryHandoverControl(submissions.required(id).control(),facts.reference(id)); }
    private Upload upload(UUID id) {
        var rows=jdbc.query("SELECT payload_sha256,byte_count,chunk_count FROM submission_upload WHERE submission_id=?",
            (rs,n)->new Upload(rs.getString(1),rs.getInt(2),rs.getInt(3)),Ids.bytes(id));return rows.isEmpty()?null:rows.getFirst();
    }
    private void receiving(SubmissionStore.Head head) {
        if(!Set.of("REGISTERED","RECEIVING").contains(head.status()))throw TemporaryHandoverIntakeFacts.changed();
    }
    private record Upload(String hash,int bytes,int count) { }
    private record Chunk(int index,int bytes,String hash) { }
}
