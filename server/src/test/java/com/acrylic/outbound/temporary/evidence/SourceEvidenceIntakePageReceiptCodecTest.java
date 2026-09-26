package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.JsonText;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceEvidenceIntakePageReceiptCodecTest {
    private static final UUID SUBMISSION=UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID AUTHORIZATION=UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID OPEN_KEY=UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    private static final UUID PAGE_KEY=UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");
    @TempDir Path directory;
    private OfflineSigner signer;
    private SourceEvidenceIntakeReceiptCodec codec;

    @BeforeEach void setUp() throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var pair=generator.generateKeyPair();
        Files.write(directory.resolve("private.pk8"),pair.getPrivate().getEncoded());
        Files.write(directory.resolve("public.der"),pair.getPublic().getEncoded());
        signer=new OfflineSigner(directory.resolve("private.pk8").toString(),directory.resolve("public.der").toString(),
            new JsonText(new ObjectMapper()));
        codec=new SourceEvidenceIntakeReceiptCodec(signer);
    }

    @Test void signedPageReceiptBindsOriginalContextAndClosesAllCapabilities() {
        var open=open();var page=page(open);var object=object(page);
        var input=new SourceEvidenceIntakePageReceipts.PageInsert(context(page),object.id());

        var receipt=codec.page(UUID.randomUUID(),PAGE_KEY,open,page,input,object,Instant.parse("2026-09-23T01:02:03.123456Z"));

        var body=SubmissionJson.parse(receipt.originalResponseBytes());
        assertThat(body.path("operationKind").asText()).isEqualTo("PAGE");
        assertThat(body.path("status").asText()).isEqualTo("PARTIAL");
        assertThat(body.path("pageHash").asText()).isEqualTo(page.pageHash());
        assertThat(body.path("objectId").asText()).isEqualTo(object.id().toString());
        assertThat(body.path("openOriginalKey").asText()).isEqualTo(OPEN_KEY.toString());
        assertThat(body.path("originalContext").path("currentSessionId").asText()).isEqualTo(input.context().currentSessionId().toString());
        assertThat(body.path("recordedAt").asText()).isEqualTo("2026-09-23T01:02:03.123Z");
        assertThat(E2aProtocol.verifyRsaReceipt(body,signer.publicKey())).isEqualTo("VERIFIED");
        assertThat(body.path("capabilities").path("localDeletionAllowed").asBoolean()).isFalse();
        assertThat(body.path("capabilities").path("releaseAllowed").asBoolean()).isFalse();
    }

    @Test void mismatchedStoredObjectCannotReceiveASignedReceipt() {
        var open=open();var page=page(open);var object=object(page);
        var forged=new SourceEvidenceIntakePageObjects.PageObject(object.id(),object.originalId(),
            new SourceEvidenceIntakeFiles.Stored("source-evidence-intake/"+"0".repeat(64)+".json","0".repeat(64),page.pageCoreBytes().length));
        var input=new SourceEvidenceIntakePageReceipts.PageInsert(context(page),object.id());

        assertThatThrownBy(() -> codec.page(UUID.randomUUID(),PAGE_KEY,open,page,input,forged,Instant.EPOCH))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("PAGE_RECEIPT_INPUT");
    }

    private SourceEvidenceIntakeOpen.Parsed open() {
        String request="""
            {"operation":"OPEN","manifestId":"manifest","manifestVersion":2,"snapshotId":"snapshot",
             "source":"TEMP","sourceId":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
             "submissionId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","workRound":3}
            """;
        return SourceEvidenceIntakeOpen.parse(request.getBytes(StandardCharsets.UTF_8),SUBMISSION,3);
    }

    private SourceEvidenceIntakePage.Parsed page(SourceEvidenceIntakeOpen.Parsed open) {
        var core=SubmissionJson.object().put("manifestId","manifest").put("manifestVersion",2)
            .put("snapshotId","snapshot").put("pageNo",1).put("pageCount",1).put("isFinal",true)
            .put("itemCount",0).put("byteCount",0).putNull("prevPageHash");
        core.putArray("items");
        var request=SubmissionJson.object().put("operation","PAGE").put("openOriginalKey",OPEN_KEY.toString());
        request.set("pageCore",core);request.put("pageHash",E2aProtocol.pageHash(core));
        return SourceEvidenceIntakePage.parse(request.toString().getBytes(StandardCharsets.UTF_8),OPEN_KEY,open);
    }

    private SourceEvidenceIntakePageObjects.PageObject object(SourceEvidenceIntakePage.Parsed page) {
        byte[] bytes=page.pageCoreBytes();String sha=ImportFiles.sha256(bytes);
        return new SourceEvidenceIntakePageObjects.PageObject(UUID.randomUUID(),UUID.randomUUID(),
            new SourceEvidenceIntakeFiles.Stored("source-evidence-intake/"+sha+".json",sha,bytes.length));
    }

    private SourceEvidenceIntakeReceipts.OpenInsert context(SourceEvidenceIntakePage.Parsed page) {
        UUID principal=UUID.randomUUID();
        return new SourceEvidenceIntakeReceipts.OpenInsert(principal,PAGE_KEY,UUID.randomUUID(),UUID.randomUUID(),
            SUBMISSION,3,UUID.randomUUID(),UUID.randomUUID(),AUTHORIZATION,principal,4,UUID.randomUUID(),"7","2",
            page.canonicalBytes(),page.requestSha256());
    }
}
