package com.acrylic.outbound.recovery;

import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.handover.HandoverRecoveredOperationsReview;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** New JVM resumes the confirmed database side after an actual application halt before COMMIT. */
public final class RecoveryReceivedScopeReleaseRestart {
    public static void crashAfterReadback(WebApplicationContext app,Path attempt,Path original) throws Exception {
        var input=RecoveryReceivedScopeReleaseReload.load(app,attempt,original);
        assertThat(input.store().inspect()).isFalse();
        app.getBean(HandoverRecoveredOperationsReview.class).project(input.store(),input.request(),true);
        assertThat(input.store().inspect()).isFalse();
        Files.write(attempt.resolve("scope-crash-confirmed.json"),RecoveryControlJson.encode(SubmissionJson.object()
            .put("pid",ProcessHandle.current().pid()).put("databaseConfirmed",true).put("externalCommitExists",false)));
        Runtime.getRuntime().halt(73);
    }
    public static void verify(WebApplicationContext app,Path attempt,Path original) throws Exception {
        var input=RecoveryReceivedScopeReleaseReload.load(app,attempt,original);
        try {
            var jdbc=app.getBean(JdbcTemplate.class); var snapshots=new E2aMigrationSnapshot(jdbc,attempt,attempt.resolve("storage"));
            JsonNode before=SubmissionJson.parse(Files.readAllBytes(attempt.resolve("scope-release-before.json")));
            JsonNode committed=SubmissionJson.parse(Files.readAllBytes(attempt.resolve("scope-release-db-committed.json")));
            assertThat(snapshots.capture()).isEqualTo(committed);
            var coordinator=new RecoveryScopeReleaseCoordinator(app.getBean(HandoverRecoveredOperationsReview.class));
            assertThatThrownBy(()->coordinator.verify(input.store(),input.request())).hasMessage("RECOVERY_RELEASE_COMMIT_REQUIRED");
            var result=coordinator.release(input.store(),input.request());
            assertThat(result.ordinaryWritesAllowed()).isFalse(); assertThat(result.localDeletionAllowed()).isFalse();
            RecoveryReceivedScopeReleaseChecks.validateCommitted(jdbc,coordinator,input.store(),input.request());
            RecoveryReceivedScopeReleaseFiles.afterCommit(coordinator,input.store(),input.request(),input.root());
            RecoveryReceivedScopeReleaseFiles.identity(coordinator,input.store(),input.request(),input.root(),input.lock());
            RecoveryReceivedScopeReleaseFaults.signedConflicts(app,input);
            var concurrent=RecoveryReceivedScopeReleaseTransactions.concurrent(coordinator,input.store(),input.request());
            assertThat(snapshots.capture()).isEqualTo(committed); RecoveryReceivedScopeReleaseChecks.conserved(before,committed);
            persist(attempt,input,result,committed,String.join(",",concurrent));
            RecoveryReceivedScopeReleaseFaults.unknownCommit(app,attempt,input);
        } finally { SecurityContextHolder.clearContext(); }
    }
    private static void persist(Path attempt,RecoveryReceivedScopeReleaseReload.Session input,RecoveryScopeReleaseCoordinator.Result result,
            JsonNode committed,String concurrent) throws Exception {
        Files.writeString(attempt.resolve("scope-release-after.json"),committed.toPrettyString());
        Files.write(attempt.resolve("scope-release-result.json"),RecoveryControlJson.encode(result.facts()));
        var checks=SubmissionJson.object().put("storeRoot",input.root().toString()).put("storeLockIdentity",input.lock())
            .put("sqlRollback",true).put("prepareOnlyDenied",true).put("dbOnlyDenied",true).put("confirmedCommitRecovered",true)
            .put("originalKeyReplay",true).put("immutableProjection",true).put("ordinaryWritesAllowed",false)
            .put("missingDatabaseDenied",true).put("partialCommitDenied",true).put("missingPrepareDenied",true)
            .put("changedStoreDenied",true).put("externalTailRollback",true).put("concurrentResults",concurrent)
            .put("restartPid",ProcessHandle.current().pid()).put("sourcePid",input.pins().path("sourcePid").asLong());
        var crash=SubmissionJson.parse(Files.readAllBytes(attempt.resolve("scope-crash-confirmed.json")));
        assertThat(crash.path("pid").asLong()).isNotEqualTo(ProcessHandle.current().pid()); checks.set("actualCrash",crash);
        Files.write(attempt.resolve("scope-release-checks.json"),RecoveryControlJson.encode(checks));
    }
    private RecoveryReceivedScopeReleaseRestart() { }
}
