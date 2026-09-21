package com.acrylic.outbound.inventory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

abstract class IdentitySetFixture extends IdentityLockFixture {
    @Autowired PlatformTransactionManager transactions;
    String changedBeforeLock(String scenario,Predicate<String> pauseSql,Callable<String> change,Callable<String> waiting) throws Exception {
        var paused=new CountDownLatch(1);var release=new CountDownLatch(1);var reached=new CountDownLatch(1);
        var evidence=new IdentityLockEvidence(jdbc);var blocker=new AtomicLong();var waiter=new AtomicLong();
        pauseWrite(pauseSql,paused,release,evidence,blocker);
        doAnswer(call->{
            boolean second=Thread.currentThread().getName().equals("l2-waiting");
            long id=evidence.connection(second?"waiting":"changing");
            if(second) { waiter.set(id);reached.countDown(); }
            Object result=call.callRealMethod();evidence.acquired(second?"waiting":"changing");return result;
        }).when(guardTarget()).lock(anyString());
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{Thread.currentThread().setName("l2-changing");return change.call();});
            Future<String> b;
            try {
                assertThat(paused.await(15,TimeUnit.SECONDS)).isTrue();
                b=pool.submit(()->{Thread.currentThread().setName("l2-waiting");return waiting.call();});
                assertThat(reached.await(15,TimeUnit.SECONDS)).isTrue();evidence.waiting(blocker.get(),waiter.get(),true);
            } finally { release.countDown(); }
            assertThat(a.get(20,TimeUnit.SECONDS)).isEqualTo("OK");String result=b.get(20,TimeUnit.SECONDS);
            evidence.save(scenario,0,result);return result;
        }
    }
    private void pauseWrite(Predicate<String> predicate,CountDownLatch paused,CountDownLatch release,IdentityLockEvidence evidence,AtomicLong id) {
        doAnswer(call->{
            Object result=call.callRealMethod();
            if(Thread.currentThread().getName().equals("l2-changing") && predicate.test(call.getArgument(0))) {
                id.set(jdbc.queryForObject("SELECT CONNECTION_ID()",Long.class));paused.countDown();
                if(!release.await(20,TimeUnit.SECONDS)) throw new AssertionError("L2 change barrier timeout");
            }
            return result;
        }).when(jdbc).update(anyString(),any(Object[].class));
    }
    String transactional(Runnable change) {
        var transaction=new TransactionTemplate(transactions);
        transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.executeWithoutResult(status->change.run());return "OK";
    }
    void recordState(String scenario,String order) throws Exception {
        String directory=System.getenv("L2_LOCK_EVIDENCE");if(directory==null)return;
        var state=new java.util.LinkedHashMap<String,Object>();state.put("order",order);
        state.put("pcWork",jdbc.queryForList("SELECT BIN_TO_UUID(operator_id) operator,status,work_round,row_version FROM pc_work WHERE order_id=UUID_TO_BIN(?)",order));
        state.put("plans",jdbc.queryForList("""
            SELECT BIN_TO_UUID(p.id) plan,BIN_TO_UUID(p.target_user_id) target,BIN_TO_UUID(p.approved_by) approver,p.status
            FROM pc_exception_plan p INNER JOIN pc_exception_case c ON c.id=p.case_id
            INNER JOIN pc_work w ON w.id=c.work_id WHERE w.order_id=UUID_TO_BIN(?) ORDER BY p.id
            """,order));
        state.put("continuations",jdbc.queryForList("SELECT status,BIN_TO_UUID(new_assignment_id) newAssignment FROM order_continuation WHERE order_id=UUID_TO_BIN(?)",order));
        state.put("commitments",jdbc.queryForList("SELECT state,COUNT(*) total FROM bale_commitment WHERE order_id=UUID_TO_BIN(?) GROUP BY state",order));
        state.put("dispatchCount",count("SELECT COUNT(*) FROM dispatch_record WHERE order_id=UUID_TO_BIN(?)",order));
        state.put("receiptCount",count("SELECT COUNT(*) FROM pc_command_receipt WHERE order_id=UUID_TO_BIN(?)",order));
        java.nio.file.Files.createDirectories(java.nio.file.Path.of(directory));json.writerWithDefaultPrettyPrinter().writeValue(
            java.nio.file.Path.of(directory,scenario+"-state-"+java.util.UUID.randomUUID()+".json").toFile(),state);
    }
}
