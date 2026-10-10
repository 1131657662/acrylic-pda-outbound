package com.acrylic.outbound.recovery;

import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.handover.HandoverRecoveredOperationsReview;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Real signed conflicts and actual MySQL commit acknowledgement loss; the resulting fence is retained. */
final class RecoveryReceivedScopeReleaseFaults {
    static void signedConflicts(WebApplicationContext app,RecoveryReceivedScopeReleaseReload.Session input) throws Exception {
        var review=app.getBean(HandoverRecoveredOperationsReview.class); var current=review.inspect(input.request());
        var f=new RecoveryOperationsFixture(input.store().approval().closed());
        var body=(ObjectNode)input.store().approval().facts();
        body.put("authoritySha256",hash(f.identity)).put("operatorId",text(f.identity,"operatorId"));
        var approved=RecoveryOperationsApproval.verify(f.request(body),current);
        var changed=new RecoveryScopeReleaseStore(approved,input.root(),input.lock());
        assertThatThrownBy(()->new RecoveryScopeReleaseCoordinator(review).release(changed,input.request()))
            .hasMessage("INITIAL_NORMAL_FILE_CONFLICT");
        var foreign=body.deepCopy(); ((ObjectNode)foreign.path("physicalReview")).put("scopeId",java.util.UUID.randomUUID().toString());
        assertThatThrownBy(()->RecoveryOperationsApproval.verify(f.request(foreign),current)).hasMessage("RECOVERY_OPERATIONS_PHYSICAL_BINDING");
    }
    static void unknownCommit(WebApplicationContext app,Path attempt,RecoveryReceivedScopeReleaseReload.Session input) throws Exception {
        var jdbc=app.getBean(JdbcTemplate.class); var review=app.getBean(HandoverRecoveredOperationsReview.class);
        var current=review.inspect(input.request()); var authority=input.store().approval().closed();
        var lost=new LostCommitDataSource(jdbc.getDataSource()); var tx=new RecoveryLeasedTransaction(lost,authority);
        var snapshot=new E2aMigrationSnapshot(jdbc,attempt,attempt.resolve("storage")); var before=snapshot.capture();
        assertThatThrownBy(()->tx.execute(status->{
            new RecoveryScopeReleaseProjection(lost,input.store(),true).apply(current,authority); return null;
        })).isInstanceOf(org.springframework.transaction.TransactionSystemException.class);
        assertThat(snapshot.capture()).isEqualTo(before);
        var coordinator=new RecoveryScopeReleaseCoordinator(review);
        assertThatThrownBy(()->coordinator.verify(input.store(),input.request())).hasMessage("CONTROL_TRANSACTION_OUTCOME_UNCONFIRMED");
        assertThatThrownBy(()->coordinator.release(input.store(),input.request())).hasMessage("CONTROL_TRANSACTION_OUTCOME_UNCONFIRMED");
        byte[] material=Files.readAllBytes(attempt.resolve("physical-restart-input.json"));
        var reload=RecoveryReceivedPhysicalRestart.mapper().readValue(material,RecoveryReceivedPhysicalRestart.Reload.class);
        assertThatThrownBy(()->new RecoveryClosedAuthority(reload.location(),reload.checkpoint(),reload.pins()))
            .hasMessage("CONTROL_TRANSACTION_OUTCOME_UNCONFIRMED");
        Path fence=authority.authorityRoot().resolve("outcome-unconfirmed.json"); assertThat(Files.isRegularFile(fence)).isTrue();
        Files.write(attempt.resolve("scope-release-unknown-commit.json"),RecoveryControlJson.encode(SubmissionJson.object()
            .put("actualMySqlCommitAcknowledgementLost",true).put("projectionAndFilesUnchanged",true)
            .put("coordinatorReleaseDenied",true).put("coordinatorVerifyDenied",true).put("newAuthorityDenied",true)
            .put("fence",fence.toString()).put("fenceSha256",sha256(Files.readAllBytes(fence)))));
    }
    private RecoveryReceivedScopeReleaseFaults() { }
}
