package com.acrylic.outbound.recovery;

import com.acrylic.outbound.auth.*;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.handover.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Development-only signer and new receiving key; original source bytes and identity remain unchanged. */
final class RecoveryRecipientFixture {
    final JdbcTemplate jdbc;
    final RecoveryOperationsFixture signer;
    final RecoveryScopeReleaseStore store;
    final HandoverRecoveredRecipientSetup.Result setup;
    final KeyPair receivingKey;
    final SessionUser recipient;
    final ObjectNode body;
    final WebApplicationContext app;
    RecoveryRecipientFixture(WebApplicationContext app,Path attempt,Path original) throws Exception {
        this.app=app; jdbc=app.getBean(JdbcTemplate.class);
        var context=RecoveryReceivedArchiveSetup.createWithHistoryWindow(jdbc,attempt,original);
        RecoveryReceivedPhysicalRestart.save(attempt,context);
        var transfer=readCanonical(Files.readAllBytes(original.resolve("received-transfer.json")));
        var keys=new RecoveryReceiptKeys(List.of(Base64.getDecoder().decode(text(transfer,"serverPublicKey"))));
        var binding=new HandoverRecoveredTemporaryBinding(context.consumer(),context.request(),context.nativeFacts());
        var history=new RecoveryReleaseNumberHistory.Request(context.request(),keys,
            new RecoveryReleaseNumberHistory.Snapshot(original.resolve("checkpoint-snapshot.json"),"1e23ca32e6c67da0ab039022ef6d4917453ec961a061aa320de3efe602d5864b"),
            new RecoveryReleaseNumberHistory.Snapshot(original.resolve("checkpoint-source-final.json"),"3393867cb13b75b9228ff067a5482b60fac6d0ec1d7caa7f9516a70f1f18694c"));
        setup=HandoverRecoveredRecipientSetup.create(app,attempt,binding,
            new RecoveryReleaseReconciliation.Request(RecoveryClosedTestSupply.of(context.source()).authority(),history,keys));
        signer=new RecoveryOperationsFixture(setup.request().reconciliation().authority());
        store=release(attempt);
        var generator=KeyPairGenerator.getInstance("EC"); generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        receivingKey=generator.generateKeyPair();
        Files.write(attempt.resolve("private/recipient-device-private.der"),receivingKey.getPrivate().getEncoded(),StandardOpenOption.CREATE_NEW);
        String username=jdbc.queryForObject("SELECT username FROM app_user WHERE id=?",String.class,Ids.bytes(context.source().identity.operatorId()));
        recipient=app.getBean(IdentityStore.class).loadUserByUsername(username); body=statement();
    }
    private RecoveryScopeReleaseStore release(Path attempt) throws Exception {
        var request=signer.request(RecoveryReceivedOperationsChecks.approval(signer,setup.reviewed()));
        var approval=RecoveryOperationsApproval.verify(request,setup.reviewed());
        Path root=Files.createDirectory(signer.directory.getParent().resolve("recipient-release-"+UUID.randomUUID()));
        Files.writeString(root.resolve("coordinator.lock"),"0");
        var store=new RecoveryScopeReleaseStore(approval,root,RecoveryWriterProtocol.fileIdentity(root.resolve("coordinator.lock")));
        authenticate(setup.supervisor());
        try { new RecoveryScopeReleaseCoordinator(app.getBean(HandoverRecoveredOperationsReview.class)).release(store,setup.request()); }
        finally { SecurityContextHolder.clearContext(); }
        RecoveryReceivedScopeReleaseReload.save(attempt,new RecoveryReceivedOperationsChecks.Result(
            new RecoveryReceivedPhysicalReviewChecks.Result(signer.closed,setup.reviewed(),setup.request()),request,approval),root,
            RecoveryWriterProtocol.fileIdentity(root.resolve("coordinator.lock")));
        return store;
    }
    private ObjectNode statement() {
        var commit=store.intent().commit(); Instant now=Instant.now().minusMillis(100).truncatedTo(ChronoUnit.MILLIS);
        var body=SubmissionJson.object().put("formatVersion",1).put("purpose","RECOVERED_TEMP_RECIPIENT_ADMISSION")
            .put("commandId",Ids.next().toString()).put("authoritySha256",signer.authority().sha256()).put("operatorId",text(signer.identity,"operatorId"))
            .put("requestedAt",now.toString()).put("expiresAt",now.plusSeconds(300).toString()).put("releaseCommitSha256",store.intent().commitSha256())
            .put("sourceManifestSha256",signer.closed.sourcePinSha256()).put("supervisorId",setup.supervisor().id().toString())
            .put("supervisorAuthVersion",Long.toString(setup.supervisor().authVersion())).put("registrarId",setup.registrar().id().toString())
            .put("registrarAuthVersion",Long.toString(setup.registrar().authVersion()));
        body.set("control",commit.path("control"));
        for(String field:List.of("warehouseId","scopeId","caseId")) body.set(field,commit.path(field));
        String key=Base64.getEncoder().encodeToString(receivingKey.getPublic().getEncoded());
        body.putObject("recipient").put("deviceId",Ids.next().toString()).put("userId",recipient.id().toString())
            .put("userAuthVersion",Long.toString(recipient.authVersion())).put("deviceCode","RECIPIENT-"+UUID.randomUUID().toString().substring(0,16))
            .put("name","隔离恢复指定接收机").put("publicKeyDer",key).put("keySha256",DeviceCrypto.sha(key));
        return body;
    }
    RecoveryRecipientAdmission.Request sign(ObjectNode value) throws Exception {
        var unsigned=value.deepCopy(); unsigned.remove("deviceProof");
        var proof=Signature.getInstance("SHA256withECDSA"); proof.initSign(receivingKey.getPrivate()); proof.update(RecoveryControlJson.encode(unsigned));
        var signed=value.deepCopy().put("deviceProof",Base64.getEncoder().encodeToString(proof.sign()));
        byte[] envelope=signer.sign(signed); Path file=signer.directory.resolve("recipient-"+UUID.randomUUID()+".json"); Files.write(file,envelope);
        return new RecoveryRecipientAdmission.Request(signer.authority(),file,sha256(envelope));
    }
    HandoverRecoveredRecipientAdmission.Result admit(RecoveryRecipientAdmission admission) {
        authenticate(setup.registrar());
        try { return app.getBean(HandoverRecoveredRecipientAdmission.class).admit(admission,setup.request()); }
        finally { SecurityContextHolder.clearContext(); }
    }
    static void authenticate(SessionUser user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,"",user.getAuthorities()));
    }
}
