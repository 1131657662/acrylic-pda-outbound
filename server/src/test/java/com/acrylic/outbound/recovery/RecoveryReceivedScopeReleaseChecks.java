package com.acrylic.outbound.recovery;

import com.acrylic.outbound.auth.IdentityStore;
import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.handover.HandoverRecoveredOperationsReview;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Real source recovery and scoped database commit; generated operations trust is development-only. */
public final class RecoveryReceivedScopeReleaseChecks {
    public static void verify(WebApplicationContext app,Path attempt,Path original) throws Exception {
        var input=RecoveryReceivedOperationsChecks.verify(app,attempt,original); var jdbc=app.getBean(JdbcTemplate.class);
        var actor=app.getBean(IdentityStore.class).loadUserByUsername("recovered-"+text(input.approval().facts(),"supervisorId"));
        var root=Files.createDirectory(input.request().file().getParent().getParent().resolve("scope-release-"+UUID.randomUUID()));
        Files.writeString(root.resolve("coordinator.lock"),"0"); String lock=RecoveryWriterProtocol.fileIdentity(root.resolve("coordinator.lock"));
        var store=new RecoveryScopeReleaseStore(input.approval(),root,lock);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,"",actor.getAuthorities()));
        try { execute(app,attempt,input,store,root,lock); }
        finally { SecurityContextHolder.clearContext(); }
        assertThat(jdbc.queryForObject("SELECT service_mode FROM server_state WHERE id=1",String.class)).isEqualTo("RECOVERY_PENDING");
    }
    private static void execute(WebApplicationContext app,Path attempt,RecoveryReceivedOperationsChecks.Result input,
            RecoveryScopeReleaseStore store,Path root,String lock) throws Exception {
        var jdbc=app.getBean(JdbcTemplate.class); var review=app.getBean(HandoverRecoveredOperationsReview.class);
        var coordinator=new RecoveryScopeReleaseCoordinator(review); var request=input.physical().request();
        var snapshots=new E2aMigrationSnapshot(jdbc,attempt,attempt.resolve("storage")); var before=snapshots.capture();
        store.prepare(); assertThat(store.inspect()).isFalse();
        assertThatThrownBy(()->coordinator.verify(store,request)).hasMessage("RECOVERY_RELEASE_COMMIT_REQUIRED");
        assertThat(snapshots.capture()).isEqualTo(before);
        RecoveryReceivedScopeReleaseFiles.beforeCommit(coordinator,store,request,root);
        RecoveryReceivedScopeReleaseTransactions.tails(jdbc,input,store,root);
        assertThat(snapshots.capture()).isEqualTo(before);
        rollback(jdbc,review,store,request); assertThat(snapshots.capture()).isEqualTo(before);
        review.project(store,request,false); assertThat(store.inspect()).isFalse();
        assertThatThrownBy(()->coordinator.verify(store,request)).hasMessage("RECOVERY_RELEASE_COMMIT_REQUIRED");
        var after=snapshots.capture(); conserved(before,after);
        Files.writeString(attempt.resolve("scope-release-before.json"),before.toPrettyString());
        Files.writeString(attempt.resolve("scope-release-db-committed.json"),after.toPrettyString());
        RecoveryReceivedScopeReleaseReload.save(attempt,input,root,lock);
    }
    private static void rollback(JdbcTemplate jdbc,HandoverRecoveredOperationsReview review,RecoveryScopeReleaseStore store,
            com.acrylic.outbound.handover.HandoverRecoveredPhysicalReview.Request request) {
        jdbc.execute("CREATE TRIGGER test_release_last_sql BEFORE INSERT ON recovery_scope_release FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='scope release tail failure'");
        try { assertThatThrownBy(()->review.project(store,request,false)).isInstanceOf(org.springframework.dao.DataAccessException.class); }
        finally { jdbc.execute("DROP TRIGGER test_release_last_sql"); }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recovery_scope_release",Integer.class)).isZero();
    }
    static void conserved(JsonNode before,JsonNode after) {
        before.path("tables").fields().forEachRemaining(e->{
            if(!e.getKey().equals("recovery_scope_release"))assertThat(after.path("tables").path(e.getKey())).as(e.getKey()).isEqualTo(e.getValue());
        });
        assertThat(after.path("tables").path("recovery_scope_release").path("rows").size()).isEqualTo(1);
        assertThat(after.path("files")).isEqualTo(before.path("files")); assertThat(after.path("flywayHistory")).isEqualTo(before.path("flywayHistory"));
    }
    static void validateCommitted(JdbcTemplate jdbc,RecoveryScopeReleaseCoordinator coordinator,RecoveryScopeReleaseStore store,
            com.acrylic.outbound.handover.HandoverRecoveredPhysicalReview.Request request) {
        var original=coordinator.verify(store,request); assertThat(coordinator.release(store,request).facts()).isEqualTo(original.facts());
        immutability(jdbc);
    }
    private static void immutability(JdbcTemplate jdbc) {
        assertThatThrownBy(()->jdbc.update("UPDATE recovery_scope_release SET recorded_at=UTC_TIMESTAMP(6)"))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM recovery_scope_release")).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    private RecoveryReceivedScopeReleaseChecks() { }
}
