package com.acrylic.outbound.temporary.draft;

import com.acrylic.outbound.audit.*;
import com.acrylic.outbound.common.JsonText;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.evidence.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.security.KeyPairGenerator;
import java.util.UUID;
import static org.mockito.Mockito.*;

final class DraftArchiveFixture {
    final UUID device=UUID.randomUUID(),warehouse=UUID.randomUUID(),evidence=UUID.randomUUID();
    final AuditArchiveStore archives=mock(AuditArchiveStore.class);
    final DraftEvidenceStore store=mock(DraftEvidenceStore.class);
    final JsonText json=new JsonText(new ObjectMapper());
    final SourceEvidenceIntakeObjectFiles objects;
    final SubmissionFiles files;
    final OfflineSigner original,rotated,unknown;
    final ObjectNode event,proof;
    final DraftEvidenceStore.Header header;
    final DraftEvidenceStore.Item item;
    final Path directory;
    DraftArchiveFixture(Path directory) throws Exception {
        this.directory=directory;objects=new SourceEvidenceIntakeObjectFiles(directory.toString());files=new SubmissionFiles(directory.toString());
        original=signer("old","");rotated=signer("new",directory.resolve("old-public.der").toString());unknown=signer("unknown","");
        event=event();proof=proof();byte[] raw=DraftEvidenceWire.bytes(event);
        var object=objects.store(new java.io.ByteArrayInputStream(raw),raw.length,DraftEvidenceWire.sha(raw));
        when(store.audit(evidence,1)).thenReturn(new DraftEvidenceStore.Item(1,"AUDIT",SubmissionJson.uuid(event,"id"),1,"RECEIVED",object));
        when(archives.find("1",device,0)).thenReturn(proof);
        header=new DraftEvidenceStore.Header(evidence,UUID.randomUUID(),warehouse,device,UUID.randomUUID(),UUID.randomUUID(),2,
            UUID.randomUUID(),new byte[]{},"", "",2,1);
        item=new DraftEvidenceStore.Item(2,"ARCHIVE",SourceEvidenceIntakeAuditArchives.recordId(device,1,0),1,"ARCHIVED",object);
    }
    DraftEvidenceArchives verifier(OfflineSigner trusted) {
        return new DraftEvidenceArchives(archives,new AuditArchiveReader(files,new SubmissionArchiveProofs(trusted)),trusted,json,store,objects);
    }
    ObjectNode wrapper() {
        return SubmissionJson.object().put("originalProof",SubmissionJson.canonical(proof))
            .put("originalSigningKey",json.encode(original.publicKey()));
    }
    private OfflineSigner signer(String prefix,String history) throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var key=generator.generateKeyPair();
        Path privatePath=directory.resolve(prefix+"-private.der"),publicPath=directory.resolve(prefix+"-public.der");
        Files.write(privatePath,key.getPrivate().getEncoded());Files.write(publicPath,key.getPublic().getEncoded());
        return new OfflineSigner(privatePath.toString(),publicPath.toString(),history,json);
    }
    private ObjectNode event() {
        return SubmissionJson.object().put("id",UUID.randomUUID().toString()).putNull("captureId").put("originDeviceId",device.toString())
            .put("operatorId",UUID.randomUUID().toString()).put("localSeq","1").put("type","RECEIVED").put("detail","")
            .put("wallMs","1").put("elapsedMs","1").put("boot",1).put("assignmentId",UUID.randomUUID().toString());
    }
    private ObjectNode proof() {
        var body=SubmissionJson.object().put("originDeviceId",device.toString()).put("afterSeq","0").put("throughSeq","1");
        body.putArray("events").add(event);byte[] bytes=DraftEvidenceWire.bytes(body);
        var proof=SubmissionJson.object().put("archiveVersion",1).put("originDeviceId",device.toString()).put("warehouseId",warehouse.toString())
            .put("recoveryEpoch","1").put("afterSeq","0").put("throughSeq","1").put("payloadSha256",files.store(bytes))
            .put("byteCount",Integer.toString(bytes.length)).put("archivedAt","2026-09-25T00:00:00Z")
            .put("retainUntil","2031-09-25T00:00:00Z").put("storagePolicy","SERVER_DURABLE_FILES_V1").put("localDeletionAllowed",false);
        var claims=SubmissionJson.object().put("iss","acrylic-outbound").put("aud","acrylic-pda-audit-archive");
        claims.set("archive",proof.deepCopy());return proof.put("signedArchive",original.sign(claims).token());
    }
}
