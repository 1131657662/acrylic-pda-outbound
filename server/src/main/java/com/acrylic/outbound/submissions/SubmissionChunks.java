package com.acrylic.outbound.submissions;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.ImportFiles;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Service
public class SubmissionChunks {
    private final SubmissionAccess access;private final SubmissionStore store;private final SubmissionFiles files;private final JdbcTemplate jdbc;private final SubmissionService service;
    public SubmissionChunks(SubmissionAccess access,SubmissionStore store,SubmissionFiles files,JdbcTemplate jdbc,SubmissionService service) {
        this.access=access;this.store=store;this.files=files;this.jdbc=jdbc;this.service=service;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode start(UUID id,JsonNode body,String epoch) {
        var control=SubmissionControl.read(id,body);var actor=access.pda(control,epoch,true);var head=store.lock(control,actor);store.current(head);receiving(head);
        String hash=text(body,"payloadSha256");long size=number(body,"byteCount"),count=number(body,"chunkCount");
        require(hash.matches("[a-f0-9]{64}") && size>0 && size<=33554432 && count==(size+262143)/262144,"分块清单无效");
        var existing=upload(id);
        if(existing==null) jdbc.update("INSERT INTO submission_upload VALUES(?,?,?,?)",Ids.bytes(id),hash,size,count);
        else equal(existing,new Upload(hash,(int)size,(int)count),"同批次分块清单不可更改");
        if(head.status().equals("REGISTERED")) store.state(id,"RECEIVING",null,null);
        return service.response(store.required(id));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode chunk(UUID id,int index,byte[] bytes,String epoch) {
        var before=store.required(id);access.pda(before.control(),epoch,true);var head=store.lockExisting(before);store.current(head);receiving(head);
        var upload=upload(id);require(upload!=null && index>=0 && index<upload.count(),"分块会话或编号无效");
        int expected=index==upload.count()-1?upload.bytes()-index*262144:262144;require(bytes.length==expected,"分块长度与清单不一致");
        String hash=files.store(bytes);var old=jdbc.queryForList("SELECT sha256 FROM submission_chunk WHERE submission_id=? AND chunk_index=?",String.class,Ids.bytes(id),index);
        if(old.isEmpty()) jdbc.update("INSERT INTO submission_chunk VALUES(?,?,?,?,?)",Ids.bytes(id),index,bytes.length,hash,"submissions/"+hash+".json");
        else if(!old.getFirst().equals(hash)) throw new BusinessException(409,"IDEMPOTENCY_CONTENT_MISMATCH","同一分块内容不可更改");
        return object().put("submissionId",id.toString()).put("chunkIndex",index).put("sha256",hash);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode finish(UUID id,String epoch) {
        var before=store.required(id);access.pda(before.control(),epoch,true);var head=store.lockExisting(before);store.current(head);receiving(head);
        if(store.payloadHash(id)!=null) return service.response(head);
        var upload=upload(id);require(upload!=null,"分块会话不存在");
        var chunks=jdbc.queryForList("SELECT sha256 FROM submission_chunk WHERE submission_id=? ORDER BY chunk_index",String.class,Ids.bytes(id));
        require(chunks.size()==upload.count(),"分块尚未完整，不可入账");var bytes=new java.io.ByteArrayOutputStream();
        chunks.forEach(hash->bytes.writeBytes(files.read(hash)));byte[] content=bytes.toByteArray();
        require(content.length==upload.bytes() && ImportFiles.sha256(content).equals(upload.hash()),"完整正文摘要或长度错误");
        var body=parse(content);equal(sha(body),upload.hash(),"上传字节必须为规范冻结正文");return service.accept(head,body);
    }
    private Upload upload(UUID id) {
        var rows=jdbc.query("SELECT payload_sha256,byte_count,chunk_count FROM submission_upload WHERE submission_id=?",(rs,n)->new Upload(rs.getString(1),rs.getInt(2),rs.getInt(3)),Ids.bytes(id));
        return rows.isEmpty()?null:rows.getFirst();
    }
    private void receiving(SubmissionStore.Head head) {
        if(head.status().equals("ABORTED") || head.status().equals("REJECTED")) throw new BusinessException(409,"SUBMISSION_"+head.status(),"旧批次已终结，不能重新上传");
    }
    private record Upload(String hash,int bytes,int count) { }
}
