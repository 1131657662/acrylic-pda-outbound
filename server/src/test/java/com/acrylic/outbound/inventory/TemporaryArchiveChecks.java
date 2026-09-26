package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

public final class TemporaryArchiveChecks {
    private final WebApplicationContext context;
    public TemporaryArchiveChecks(WebApplicationContext context) { this.context=context; }
    public void verify() throws Exception {
        var f=new TemporaryConfirmationFixture(context,false);var driver=new TemporaryArchiveDriver(context);
        String token=f.source.f.tokens.path("accessToken").asText();var id=UUID.fromString(f.source.id());
        var request=SubmissionJson.object().put("payloadSha256",SubmissionJson.sha(f.source.body)).put("receiptSha256","0".repeat(64));
        reject("ARCHIVE_NOT_READY",()->driver.archive(id,token,request));
        var command=SubmissionJson.parse(f.execute(f.command("NORMAL_CONFIRM",f.operator()),f.operator()));
        var receipt=command.at("/businessDecision/submissionReceipt");request.put("receiptSha256",SubmissionJson.sha(receipt));
        var before=f.state();atomicity(f,driver,token,request);var proof=driver.archive(id,token,request);
        assertThat(driver.archive(id,token,request)).isEqualTo(proof);assertThat(f.state()).isEqualTo(before);
        assertThat(proof.path("archiveVersion").asInt()).isEqualTo(2);assertThat(proof.path("localDeletionAllowed").asBoolean()).isFalse();
        assertThat(Instant.parse(proof.path("retainUntil").asText())).isEqualTo(Instant.parse(proof.path("archivedAt").asText()).atOffset(ZoneOffset.UTC).plusYears(5).toInstant());
        for(String field:List.of("payloadSha256","receiptSha256"))reject("ARCHIVE_CONTENT_MISMATCH",()->driver.archive(id,token,request.deepCopy().put(field,"f".repeat(64))));
        corruptFiles(f,driver,token,request,proof);corruptIndex(f,driver,token,request,proof);barriers(f,driver,token,request);
        assertThat(driver.archive(id,token,request)).isEqualTo(proof);assertThat(f.state()).isEqualTo(before);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM submission_archive WHERE submission_id=UUID_TO_BIN(?)",Integer.class,id.toString())).isEqualTo(1);
    }
    private void atomicity(TemporaryConfirmationFixture f,TemporaryArchiveDriver driver,String token,ObjectNode request) {
        var failing=new SubmissionArchiveStore(f.jdbc) {
            @Override public void insert(UUID id,String epoch,JsonNode proof) { super.insert(id,epoch,proof);throw new IllegalStateException("injected after archive insert"); }
        };
        var service=driver.service(failing,f.bean(SubmissionArchiveProofs.class));var id=UUID.fromString(f.source.id());
        var outer=new org.springframework.transaction.support.TransactionTemplate(f.bean(org.springframework.transaction.PlatformTransactionManager.class));
        assertThatThrownBy(()->outer.execute(status->driver.archive(service,id,token,"1",request)))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("outer commit boundary");
        assertThatThrownBy(()->driver.archive(service,id,token,"1",request)).isInstanceOf(IllegalStateException.class);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM submission_archive WHERE submission_id=UUID_TO_BIN(?)",Integer.class,id.toString())).isZero();
        var badSigner=new SubmissionArchiveProofs(f.bean(OfflineSigner.class)) {
            @Override public ObjectNode sign(ObjectNode value) { throw new IllegalStateException("injected signing failure"); }
        };
        assertThatThrownBy(()->driver.archive(driver.service(f.bean(SubmissionArchiveStore.class),badSigner),id,token,"1",request)).isInstanceOf(IllegalStateException.class);
    }
    private void corruptFiles(TemporaryConfirmationFixture f,TemporaryArchiveDriver driver,String token,ObjectNode request,JsonNode proof) throws Exception {
        var storage=Path.of(context.getEnvironment().getRequiredProperty("outbound.storage"),"submissions");
        for(String field:List.of("payloadSha256","receiptSha256","commandResponseSha256")) {
            var path=storage.resolve(proof.path(field).asText()+".json");byte[] original=Files.readAllBytes(path);
            var held=path.resolveSibling(path.getFileName()+".held-test");Files.move(path,held);
            try { reject("ARCHIVE_EVIDENCE_UNAVAILABLE",()->driver.archive(UUID.fromString(f.source.id()),token,request));assertThat(Files.exists(path)).isFalse(); }
            finally { Files.move(held,path); }
            try { Files.writeString(path,"tampered");reject("ARCHIVE_EVIDENCE_UNAVAILABLE",()->driver.archive(UUID.fromString(f.source.id()),token,request)); }
            finally { Files.write(path,original); }
        }
    }
    private void corruptIndex(TemporaryConfirmationFixture f,TemporaryArchiveDriver driver,String token,ObjectNode request,JsonNode proof) {
        var id=UUID.fromString(f.source.id());String old=SubmissionJson.canonical(proof);
        f.jdbc.update("UPDATE submission_archive SET confirmation_text=? WHERE submission_id=UUID_TO_BIN(?)",((ObjectNode)proof.deepCopy()).put("localDeletionAllowed",true).toString(),id.toString());
        try { reject("ARCHIVE_EVIDENCE_UNAVAILABLE",()->driver.archive(id,token,request)); }
        finally { f.jdbc.update("UPDATE submission_archive SET confirmation_text=? WHERE submission_id=UUID_TO_BIN(?)",old,id.toString()); }
        String oldEvent=f.jdbc.queryForObject("SELECT decision_kind FROM temporary_decision_event WHERE receipt_id=UUID_TO_BIN(?)",String.class,proof.path("commandReceiptId").asText());
        f.jdbc.update("UPDATE temporary_decision_event SET context_bytes=? WHERE receipt_id=UUID_TO_BIN(?)",new byte[]{123,125},proof.path("commandReceiptId").asText());
        try { reject("ARCHIVE_EVIDENCE_UNAVAILABLE",()->driver.archive(id,token,request)); }
        finally {
            var original=(ObjectNode)SubmissionJson.parse(f.bean(SubmissionFiles.class).read(proof.path("commandResponseSha256").asText()));original.remove("signedReceipt");
            f.jdbc.update("UPDATE temporary_decision_event SET context_bytes=?,decision_kind=? WHERE receipt_id=UUID_TO_BIN(?)",
                SubmissionJson.canonical(original).getBytes(java.nio.charset.StandardCharsets.UTF_8),oldEvent,proof.path("commandReceiptId").asText());
        }
    }
    private void barriers(TemporaryConfirmationFixture f,TemporaryArchiveDriver driver,String token,ObjectNode request) throws Exception {
        var other=new TemporaryConfirmationFixture(context,false);var id=UUID.fromString(f.source.id());
        reject("SUBMISSION_NOT_FOUND",()->driver.archive(id,other.source.f.tokens.path("accessToken").asText(),request));
        f.jdbc.update("UPDATE server_state SET service_mode='RECOVERY_PENDING' WHERE id=1");
        try { assertThatThrownBy(()->driver.archive(id,token,request)).isInstanceOf(BusinessException.class); }
        finally { f.jdbc.update("UPDATE server_state SET service_mode='NORMAL' WHERE id=1"); }
        f.jdbc.update("UPDATE server_state SET recovery_epoch=2 WHERE id=1");
        try { reject("SERVER_RECOVERY_EPOCH_MISMATCH",()->driver.archive(id,token,request)); }
        finally { f.jdbc.update("UPDATE server_state SET recovery_epoch=1 WHERE id=1"); }
    }
    private static void reject(String code,org.assertj.core.api.ThrowableAssert.ThrowingCallable action) { TemporaryConfirmationChecks.reject(code,action); }
}
