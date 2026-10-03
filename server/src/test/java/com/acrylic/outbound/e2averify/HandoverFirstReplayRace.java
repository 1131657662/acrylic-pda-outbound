package com.acrylic.outbound.e2averify;

import java.util.List;
import java.util.concurrent.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** Holds the common identity prefix until two distinct first command transactions are observed waiting. */
final class HandoverFirstReplayRace {
    private final JdbcTemplate jdbc;
    private final java.nio.file.Path attempt;
    HandoverFirstReplayRace(HandoverRollbackFixture fixture,java.nio.file.Path attempt){jdbc=fixture.jdbc;this.attempt=attempt;}
    List<Outcome> execute(Callable<byte[]> call)throws Exception {
        try(var blocker=jdbc.getDataSource().getConnection();var pool=Executors.newFixedThreadPool(2)) {
            blocker.setAutoCommit(false);
            try {
                try(var statement=blocker.createStatement()){statement.executeQuery("SELECT id FROM user_management_guard WHERE id=1 FOR UPDATE").close();}
                var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
                var first=pool.submit(()->invoke(call,ready,start));var second=pool.submit(()->invoke(call,ready,start));
                assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();awaitTwoDatabaseWaiters(List.of(first,second));
                blocker.commit();return List.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS));
            }finally{blocker.rollback();pool.shutdownNow();}
        }
    }
    private void awaitTwoDatabaseWaiters(List<Future<Outcome>> workers)throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        while(System.nanoTime()<deadline) {
            var waits=jdbc.queryForList("SELECT ENGINE_TRANSACTION_ID,THREAD_ID,OBJECT_NAME,LOCK_TYPE,LOCK_MODE,LOCK_STATUS,LOCK_DATA "
                +"FROM performance_schema.data_locks WHERE OBJECT_SCHEMA=DATABASE() AND OBJECT_NAME='user_management_guard' AND LOCK_STATUS='WAITING' ORDER BY THREAD_ID");
            if(waits.stream().map(row->row.get("THREAD_ID")).distinct().count()==2) {
                assertThat(waits.stream().map(row->row.get("ENGINE_TRANSACTION_ID")).distinct().count()).isEqualTo(2);
                java.nio.file.Files.writeString(attempt.resolve("first-replay-lock-waiters.json"),new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(waits).toPrettyString());return;
            }
            assertThat(workers).allMatch(worker->!worker.isDone());Thread.sleep(50);
        }
        throw new AssertionError("Two real identity lock waiters not observed");
    }
    private static Outcome invoke(Callable<byte[]> call,CountDownLatch ready,CountDownLatch start)throws Exception {
        ready.countDown();if(!start.await(10,TimeUnit.SECONDS))throw new AssertionError("Start barrier timed out");
        try{return new Outcome("OK",call.call());}catch(com.acrylic.outbound.common.BusinessException failure){return new Outcome(failure.code(),null);}
    }
    record Outcome(String code,byte[] response){}
}
