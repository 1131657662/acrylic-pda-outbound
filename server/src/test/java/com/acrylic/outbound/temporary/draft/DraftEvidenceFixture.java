package com.acrylic.outbound.temporary.draft;

import com.acrylic.outbound.common.JsonText;
import com.acrylic.outbound.pda.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.LocalDraftStopProtocol;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeObjectFiles;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import static org.mockito.Mockito.*;

final class DraftEvidenceFixture {
    final DraftEvidenceStore store=mock(DraftEvidenceStore.class);
    final DraftEvidenceAccess access=mock(DraftEvidenceAccess.class);
    final DraftEvidencePreparation preparation=mock(DraftEvidencePreparation.class);
    final JsonNode vector;
    final UUID key=UUID.randomUUID();
    final byte[] open;
    final LocalDraftStopProtocol.Expected expected;
    final PdaSessions.Principal actor;
    final DraftEvidenceStore.Header header;
    DraftEvidenceFixture() throws Exception {
        try(var input=getClass().getResourceAsStream("/e2a/local-draft-snapshots/committed-active.json")) {
            vector=SubmissionJson.parse(input.readAllBytes());
        }
        var core=vector.path("proof").path("core");
        var publicKey=KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(vector.path("publicKey").asText())));
        expected=new LocalDraftStopProtocol.Expected(id(core,"evidenceId"),id(core,"clientOrderId"),id(core,"creatorDeviceId"),id(core,"authorizationId"),
            id(core,"warehouseId"),id(core,"originalOperatorId"),id(core,"snapshotId"),core.path("sourceRecoveryEpoch").longValue(),publicKey);
        actor=new PdaSessions.Principal(UUID.randomUUID(),expected.originalOperatorId(),"synthetic",expected.creatorDeviceId(),expected.warehouseId(),"1","1");
        var body=SubmissionJson.object();body.set("metadata",vector.path("metadata"));body.set("proof",vector.path("proof"));open=DraftEvidenceWire.bytes(body);
        header=new DraftEvidenceStore.Header(expected.evidenceId(),expected.clientOrderId(),expected.warehouseId(),expected.creatorDeviceId(),expected.originalOperatorId(),
            expected.authorizationId(),expected.sourceRecoveryEpoch(),key,open,DraftEvidenceWire.sha(open),core.path("rootSha256").asText(),core.path("recordCount").intValue(),core.path("byteCount").longValue());
        when(access.current()).thenReturn(actor);when(access.lock("1")).thenReturn(actor);
        when(preparation.require(any(),eq(actor))).thenReturn(expected);when(store.required(header.evidence())).thenReturn(header);
    }
    DraftEvidenceItemRequest request(int index) {
        var item=vector.path("items").get(index);byte[] raw=DraftEvidenceWire.bytes(item.path("raw"));
        return new DraftEvidenceItemRequest(header.evidence(),UUID.randomUUID(),index+1,item.path("kind").asText(),id(item,"recordId"),
            item.path("deviceSeq").longValue(),item.path("recordStatus").asText(),raw.length,item.path("bytesSha256").asText(),raw);
    }
    DraftEvidenceStore.Item item(DraftEvidenceItemRequest request) {
        return new DraftEvidenceStore.Item(request.ordinal(),request.kind(),request.record(),request.sequence(),request.status(),
            new SourceEvidenceIntakeObjectFiles.Stored("source-evidence-intake/objects/"+request.sha()+".bin",request.sha(),request.length()));
    }
    static OfflineSigner signer(Path directory) throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var pair=generator.generateKeyPair();
        Files.write(directory.resolve("private.pk8"),pair.getPrivate().getEncoded());Files.write(directory.resolve("public.der"),pair.getPublic().getEncoded());
        return new OfflineSigner(directory.resolve("private.pk8").toString(),directory.resolve("public.der").toString(),new JsonText(new ObjectMapper()));
    }
    private UUID id(JsonNode value,String field) { return UUID.fromString(value.path(field).asText()); }
}
