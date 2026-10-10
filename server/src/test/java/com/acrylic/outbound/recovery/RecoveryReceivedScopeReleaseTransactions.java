package com.acrylic.outbound.recovery;

import com.acrylic.outbound.handover.HandoverRecoveredPhysicalReview;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** Real MySQL rollback and concurrent same-key completion, with explicit fail-closed refusal codes. */
final class RecoveryReceivedScopeReleaseTransactions {
    static void tails(JdbcTemplate jdbc,RecoveryReceivedOperationsChecks.Result input,RecoveryScopeReleaseStore store,Path root) throws Exception {
        for(Path file:List.of(input.request().file(),root.resolve("scope-release-prepare.json"))) {
            byte[] original=Files.readAllBytes(file); var tx=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
            try {
                assertThatThrownBy(()->tx.executeWithoutResult(status->{
                    new RecoveryScopeReleaseProjection(jdbc.getDataSource(),store,false).apply(input.physical().reviewed(),input.physical().authority());
                    assertThat(jdbc.update("UPDATE warehouse SET name=CONCAT(name,'-scope-tail')")).isPositive();
                    try { Files.writeString(file,"tampered"); }
                    catch(java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
                })).isInstanceOf(RecoveryControlException.class);
            } finally { RecoveryReceivedScopeReleaseFiles.preserve(file); Files.write(file,original); }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recovery_scope_release",Integer.class)).isZero();
        }
    }
    static List<String> concurrent(RecoveryScopeReleaseCoordinator coordinator,RecoveryScopeReleaseStore store,
            HandoverRecoveredPhysicalReview.Request request) throws Exception {
        var auth=SecurityContextHolder.getContext().getAuthentication(); var barrier=new CyclicBarrier(2);
        try(var workers=Executors.newFixedThreadPool(2)) {
            List<Future<String>> results=new ArrayList<>();
            for(int i=0;i<2;i++) results.add(workers.submit(()->{
                SecurityContextHolder.getContext().setAuthentication(auth);
                try {
                    barrier.await(10,TimeUnit.SECONDS); coordinator.release(store,request); return "COMMITTED";
                } catch(RecoveryControlException error) { return error.getMessage(); }
                finally { SecurityContextHolder.clearContext(); }
            }));
            var codes=new ArrayList<String>(); for(var result:results) codes.add(result.get(60,TimeUnit.SECONDS));
            assertThat(codes).allMatch(code->Set.of("COMMITTED","CONTROL_COORDINATOR_BUSY","CONTROL_TRANSACTION_OUTCOME_UNCONFIRMED").contains(code));
            coordinator.verify(store,request); return List.copyOf(codes);
        }
    }
    private RecoveryReceivedScopeReleaseTransactions() { }
}
