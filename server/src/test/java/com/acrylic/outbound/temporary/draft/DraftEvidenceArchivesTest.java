package com.acrylic.outbound.temporary.draft;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DraftEvidenceArchivesTest {
    @TempDir Path directory;
    private DraftArchiveFixture f;
    @BeforeEach void setup() throws Exception { f=new DraftArchiveFixture(directory); }
    @Test void configuredHistoricalKeyVerifiesOriginalArchiveAndSnapshotBytes() {
        f.verifier(f.rotated).require(f.header,f.item,DraftEvidenceWire.bytes(f.wrapper()));
        verify(f.store).audit(f.evidence,1);
        assertThat(f.proof.path("localDeletionAllowed").booleanValue()).isFalse();
    }
    @Test void uploadedHistoricalKeyCannotAuthorizeUnknownIssuer() {
        assertThatThrownBy(()->f.verifier(f.unknown).require(f.header,f.item,DraftEvidenceWire.bytes(f.wrapper())))
            .isInstanceOf(BusinessException.class);
    }
    @Test void substitutedUploadedKeyFailsEvenWithValidOriginalSignature() {
        var wrapper=f.wrapper().put("originalSigningKey",f.json.encode(f.unknown.publicKey()));
        assertThatThrownBy(()->f.verifier(f.rotated).require(f.header,f.item,DraftEvidenceWire.bytes(wrapper)))
            .isInstanceOf(BusinessException.class);
    }
    @Test void missingServerIndexFailsEvenWithValidHistoricalSignature() {
        when(f.archives.find("1",f.device,0)).thenReturn(null);
        assertThatThrownBy(()->f.verifier(f.rotated).require(f.header,f.item,DraftEvidenceWire.bytes(f.wrapper())))
            .isInstanceOf(BusinessException.class);
    }
    @Test void validArchiveCannotBlessChangedSnapshotEvent() throws Exception {
        var changed=f.event.deepCopy().put("detail","changed");byte[] raw=DraftEvidenceWire.bytes(changed);
        var object=f.objects.store(new java.io.ByteArrayInputStream(raw),raw.length,DraftEvidenceWire.sha(raw));
        when(f.store.audit(f.evidence,1)).thenReturn(new DraftEvidenceStore.Item(1,"AUDIT",SubmissionJson.uuid(f.event,"id"),1,"RECEIVED",object));
        assertThatThrownBy(()->f.verifier(f.rotated).require(f.header,f.item,DraftEvidenceWire.bytes(f.wrapper())))
            .isInstanceOf(BusinessException.class);
    }
    @Test void corruptedArchiveFileFailsWithIntactProofAndIndex() throws Exception {
        Files.write(directory.resolve("submissions").resolve(f.proof.path("payloadSha256").asText()+".json"),new byte[]{0});
        assertThatThrownBy(()->f.verifier(f.rotated).require(f.header,f.item,DraftEvidenceWire.bytes(f.wrapper())))
            .isInstanceOf(BusinessException.class);
    }
    @Test void signedForeignWarehouseArchiveRemainsRejected() {
        f.proof.put("warehouseId",java.util.UUID.randomUUID().toString());
        var unsigned=f.proof.deepCopy();unsigned.remove("signedArchive");
        var claims=SubmissionJson.object().put("iss","acrylic-outbound").put("aud","acrylic-pda-audit-archive");
        claims.set("archive",unsigned);f.proof.put("signedArchive",f.original.sign(claims).token());
        assertThatThrownBy(()->f.verifier(f.rotated).require(f.header,f.item,DraftEvidenceWire.bytes(f.wrapper())))
            .isInstanceOf(BusinessException.class);
    }
}
