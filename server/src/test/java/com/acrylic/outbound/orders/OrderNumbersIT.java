package com.acrylic.outbound.orders;

import com.acrylic.outbound.common.BusinessException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class OrderNumbersIT {
    @Autowired OrderNumbers numbers;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @Test void resetsAtShanghaiMidnightAndKeepsOldDayCounter() {
        var tx=new TransactionTemplate(transactions);
        tx.executeWithoutResult(status->{
            assertThat(numbers.next(Instant.parse("2081-12-31T15:59:59.999Z"))).isEqualTo("FH208112310001");
            assertThat(numbers.next(Instant.parse("2081-12-31T16:00:00Z"))).isEqualTo("FH208201010001");
            assertThat(numbers.next(Instant.parse("2081-12-31T15:59:59Z"))).isEqualTo("FH208112310002");
            status.setRollbackOnly();
        });
    }

    @Test void growsPastFourDigitsWithoutReusingAnEarlierNumber() {
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            jdbc.update("INSERT INTO order_number_counter VALUES(?,?)",LocalDate.of(2082,2,1),9999);
            assertThat(numbers.next(Instant.parse("2082-02-01T00:00:00Z"))).isEqualTo("FH2082020110000");
            status.setRollbackOnly();
        });
    }

    @Test void exhaustedCounterFailsWithoutWrappingToZero() {
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            jdbc.update("INSERT INTO order_number_counter VALUES(?,?)",LocalDate.of(2082,3,1),Integer.MAX_VALUE);
            assertThatThrownBy(()->numbers.next(Instant.parse("2082-03-01T00:00:00Z")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("当日订单编号已达上限");
            assertThat(jdbc.queryForObject("SELECT last_sequence FROM order_number_counter WHERE business_date=?",Integer.class,LocalDate.of(2082,3,1)))
                .isEqualTo(Integer.MAX_VALUE);
            status.setRollbackOnly();
        });
    }

    @Test void rolledBackAllocationDoesNotLeaveCounterOrChangeNextCommittedNumber() {
        var instant=Instant.parse("2082-04-01T00:00:00Z");var tx=new TransactionTemplate(transactions);
        tx.executeWithoutResult(status->{ assertThat(numbers.next(instant)).isEqualTo("FH208204010001");status.setRollbackOnly(); });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_number_counter WHERE business_date=?",Integer.class,LocalDate.of(2082,4,1))).isZero();
        tx.executeWithoutResult(status->{ assertThat(numbers.next(instant)).isEqualTo("FH208204010001");status.setRollbackOnly(); });
    }

    @Test void simultaneousFirstAllocationsUseOneDurableGlobalSequence() throws Exception {
        var date=LocalDate.of(2200+new Random().nextInt(1000),1,1).plusDays(new Random().nextInt(365));
        var instant=date.atStartOfDay(ZoneOffset.UTC).toInstant();var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(4)) {
            var results=new ArrayList<Future<String>>();
            for(int i=0;i<12;i++) results.add(pool.submit(()->{
                if(!start.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                return new TransactionTemplate(transactions).execute(status->numbers.next(instant));
            }));
            start.countDown();var values=new ArrayList<String>();
            for(var result:results) values.add(result.get(15,TimeUnit.SECONDS));
            assertThat(values).doesNotHaveDuplicates().hasSize(12);
            assertThat(jdbc.queryForObject("SELECT last_sequence FROM order_number_counter WHERE business_date=?",Integer.class,date)).isEqualTo(12);
            String next=new TransactionTemplate(transactions).execute(status->numbers.next(instant));
            assertThat(next).endsWith("0013");
        } finally { jdbc.update("DELETE FROM order_number_counter WHERE business_date=?",date); }
    }
}
