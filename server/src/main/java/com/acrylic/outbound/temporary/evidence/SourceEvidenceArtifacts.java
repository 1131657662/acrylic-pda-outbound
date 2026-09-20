package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static com.acrylic.outbound.temporary.evidence.SourceEvidenceQueries.*;

@Component
public class SourceEvidenceArtifacts {
    private final SourceEvidenceQueries queries;private final SourceEvidenceFiles files;
    public SourceEvidenceArtifacts(SourceEvidenceQueries queries,SourceEvidenceFiles files) { this.queries=queries;this.files=files; }
    View inspect(com.acrylic.outbound.submissions.SubmissionStore.Head head) {
        UUID submission=head.control().id();
        var payload=queries.payload(submission);var declared=queries.upload(submission);var chunks=queries.chunks(submission);
        var received=object().put("received",payload!=null).putNull("sha256").putNull("bytes").put("fileVerified",false);
        if(payload!=null) { payload(head);received.put("sha256",payload.sha256()).put("bytes",Integer.toString(payload.bytes())).put("fileVerified",true); }
        if((payload==null)!=(head.order()==null))throw SourceEvidenceAccess.corrupt();
        var upload=object().put("present",declared!=null).putNull("declaredSha256").putNull("declaredBytes").putNull("declaredChunkCount");
        var receivedChunks=upload.putArray("receivedChunks");
        if(declared!=null) {
            geometry(declared,chunks);upload.put("declaredSha256",declared.sha256()).put("declaredBytes",Integer.toString(declared.bytes()))
                .put("declaredChunkCount",declared.chunkCount());
            verifyChunks(declared,chunks);
            for(var chunk:chunks) { receivedChunks.add(object().put("index",chunk.index())
                .put("sha256",chunk.file().sha256()).put("bytes",Integer.toString(chunk.file().bytes())).put("fileVerified",true)); }
            if(payload!=null && (!payload.sha256().equals(declared.sha256()) || payload.bytes()!=declared.bytes()))throw SourceEvidenceAccess.corrupt();
        } else if(!chunks.isEmpty())throw SourceEvidenceAccess.corrupt();
        upload.put("receivedChunkCount",chunks.size()).put("allDeclaredChunksReceived",declared!=null && chunks.size()==declared.chunkCount())
            .put("acceptedPayload",payload!=null);
        return new View(payload,received,upload);
    }
    byte[] payload(com.acrylic.outbound.submissions.SubmissionStore.Head head) {
        byte[] bytes=files.read(queries.payload(head.control().id()),33554432);
        try {
            var body=parse(bytes);
            if(!com.acrylic.outbound.submissions.SubmissionControl.read(head.control().id(),body).equals(head.control())
                || !body.path("submissionId").asText().equals(head.control().id().toString()))throw SourceEvidenceAccess.corrupt();
        } catch(RuntimeException invalid) { throw SourceEvidenceAccess.corrupt(); }
        return bytes;
    }
    private void verifyChunks(Upload upload,List<Chunk> chunks) {
        try {
            var digest=java.security.MessageDigest.getInstance("SHA-256");
            for(var chunk:chunks)digest.update(files.read(chunk.file(),262144));
            if(chunks.size()==upload.chunkCount() && !java.util.HexFormat.of().formatHex(digest.digest()).equals(upload.sha256()))
                throw SourceEvidenceAccess.corrupt();
        } catch(java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    byte[] chunk(UUID id,int index) {
        if(index<0 || index>127)throw new BusinessException(400,"INVALID_CHUNK_INDEX","分块索引必须为0至127");
        var declared=queries.upload(id);if(declared==null)throw SourceEvidenceAccess.missing();
        var chunks=queries.chunks(id);geometry(declared,chunks);
        var found=chunks.stream().filter(c->c.index()==index).findFirst().orElseThrow(SourceEvidenceAccess::missing);
        return files.read(found.file(),262144);
    }
    static void geometry(Upload upload,List<Chunk> chunks) {
        if(upload.bytes()<1 || upload.bytes()>33554432 || upload.chunkCount()!=(upload.bytes()+262143)/262144
            || !upload.sha256().matches("[a-f0-9]{64}"))throw SourceEvidenceAccess.corrupt();
        var seen=new HashSet<Integer>();
        for(var chunk:chunks)if(!seen.add(chunk.index()) || chunk.index()<0 || chunk.index()>=upload.chunkCount()
            || chunk.file().bytes()!=Math.min(262144,upload.bytes()-chunk.index()*262144))throw SourceEvidenceAccess.corrupt();
    }
    record View(FileRecord payload,JsonNode received,JsonNode upload) { }
}
