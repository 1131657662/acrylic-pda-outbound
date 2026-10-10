package com.acrylic.outbound.recovery;

import com.acrylic.outbound.auth.IdentityStore;
import com.acrylic.outbound.handover.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Reuses original independent pins, archive material and offline approval; no reenrollment or signing. */
public final class RecoveryReceivedScopeReleaseReload {
    static void save(Path attempt,RecoveryReceivedOperationsChecks.Result input,Path root,String lock) throws Exception {
        var authority=input.request().authority();
        var value=com.acrylic.outbound.submissions.SubmissionJson.object().put("sourcePid",ProcessHandle.current().pid())
            .put("identityFile",authority.identityFile().toString()).put("identitySha256",authority.sha256())
            .put("approvalFile",input.request().file().toString()).put("approvalSha256",input.request().independentSha256())
            .put("storeRoot",root.toString()).put("storeLockIdentity",lock);
        byte[] bytes=RecoveryControlJson.encode(value); Files.write(attempt.resolve("scope-restart-input.json"),bytes);
        Files.writeString(attempt.resolve("scope-restart-input.sha256"),sha256(bytes));
    }
    public static Session load(WebApplicationContext app,Path attempt,Path original) throws Exception {
        byte[] bytes=Files.readAllBytes(attempt.resolve("scope-restart-input.json"));
        assertThat(sha256(bytes)).isEqualTo(Files.readString(attempt.resolve("scope-restart-input.sha256")));
        var value=readCanonical(bytes); assertThat(value.path("sourcePid").asLong()).isNotEqualTo(ProcessHandle.current().pid());
        var request=request(app.getBean(JdbcTemplate.class),attempt,original);
        var authority=new RecoveryOperationsAuthority(request.reconciliation().authority(),Path.of(text(value,"identityFile")),text(value,"identitySha256"));
        var approvalRequest=new RecoveryOperationsApproval.Request(authority,Path.of(text(value,"approvalFile")),text(value,"approvalSha256"));
        var signed=readCanonical(Files.readAllBytes(approvalRequest.file()));
        var actor=app.getBean(IdentityStore.class).loadUserByUsername("recovered-"+text(signed.path("approval"),"supervisorId"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,"",actor.getAuthorities()));
        var review=app.getBean(HandoverRecoveredOperationsReview.class);
        var approved=review.verify(approvalRequest,review.inspect(request),request);
        Path root=Path.of(text(value,"storeRoot")); String lock=text(value,"storeLockIdentity");
        return new Session(new RecoveryScopeReleaseStore(approved,root,lock),request,root,lock,value);
    }
    private static HandoverRecoveredPhysicalReview.Request request(JdbcTemplate jdbc,Path attempt,Path original) throws Exception {
        byte[] bytes=Files.readAllBytes(attempt.resolve("physical-restart-input.json"));
        assertThat(sha256(bytes)).isEqualTo(Files.readString(attempt.resolve("physical-restart-input.sha256")));
        var reload=RecoveryReceivedPhysicalRestart.mapper().readValue(bytes,RecoveryReceivedPhysicalRestart.Reload.class);
        var authority=new RecoveryClosedAuthority(reload.location(),reload.checkpoint(),reload.pins());
        var transfer=readCanonical(Files.readAllBytes(original.resolve("received-transfer.json")));
        var keys=new RecoveryReceiptKeys(List.of(Base64.getDecoder().decode(text(transfer,"serverPublicKey"))));
        var consumer=new RecoveryReceivedTemporaryArchiveConsumer(jdbc.getDataSource(),authority,keys);
        var binding=new HandoverRecoveredTemporaryBinding(consumer,reload.request(),reload.nativeFacts());
        var history=new RecoveryReleaseNumberHistory.Request(reload.request(),keys,
            new RecoveryReleaseNumberHistory.Snapshot(original.resolve("checkpoint-snapshot.json"),"1e23ca32e6c67da0ab039022ef6d4917453ec961a061aa320de3efe602d5864b"),
            new RecoveryReleaseNumberHistory.Snapshot(original.resolve("checkpoint-source-final.json"),"3393867cb13b75b9228ff067a5482b60fac6d0ec1d7caa7f9516a70f1f18694c"));
        return new HandoverRecoveredPhysicalReview.Request(Files.readAllBytes(attempt.resolve("recovered-fault/fault-command.json")),
            binding,new RecoveryReleaseReconciliation.Request(authority,history,keys));
    }
    public record Session(RecoveryScopeReleaseStore store,HandoverRecoveredPhysicalReview.Request request,Path root,String lock,JsonNode pins) { }
    private RecoveryReceivedScopeReleaseReload() { }
}
