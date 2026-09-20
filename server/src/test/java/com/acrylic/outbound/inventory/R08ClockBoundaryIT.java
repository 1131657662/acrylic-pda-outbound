package com.acrylic.outbound.inventory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest @AutoConfigureMockMvc
class R08ClockBoundaryIT extends R08CenterFixture {
    @Autowired PlatformTransactionManager transactions;

    @Test void rangeAndSummaryIncludeConfirmedFactsWhenDatabaseClockLeadsJvm() {
        databaseAhead(() -> {
            var order=completed();String key=UUID.randomUUID().toString();
            var accepted=create(key,"XLSX");var cutoff=snapshot(id(accepted)).metadata().cutoff();
            assertThat(snapshot(id(accepted)).orders()).extracting(o->o.id()).containsExactly(order.order);
            String day=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString();
            var summary=api.data(get("/api/v1/exports/summary").param("fromDate",day).param("throughDate",day));
            assertThat(summary.path("orders")).extracting(o->o.path("id").asText()).containsExactly(order.order);
            jdbc.execute("SET timestamp = 0");
            var replay=create(key,"XLSX");
            assertThat(replay.path("id")).isEqualTo(accepted.path("id"));
            assertThat(snapshot(id(replay)).metadata().cutoff()).isEqualTo(cutoff);
        });
    }

    @Test void singleOrderSnapshotUsesSameDatabaseClockAsConfirmation() {
        databaseAhead(() -> {
            var order=completed();
            var accepted=api.data(post("/api/v1/orders/"+order.order+"/exports")
                    .header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json")
                    .content("{\"format\":\"XLSX\",\"basis\":\"ORIGINAL_CONFIRMED\"}"));
            var frozen=snapshot(id(accepted));
            assertThat(frozen.orders()).extracting(o->o.id()).containsExactly(order.order);
            assertThat(frozen.metadata().cutoff()).isAfter(Instant.now().plusSeconds(2));
        });
    }

    private void databaseAhead(CheckedAction action) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.execute("SET timestamp = UNIX_TIMESTAMP() + 10");
            try {
                assertThat(jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)",Timestamp.class).toInstant())
                        .isAfter(Instant.now().plusSeconds(5));
                action.run();
            } catch(Exception error) { throw new AssertionError(error); }
            finally { jdbc.execute("SET timestamp = 0");status.setRollbackOnly(); }
        });
    }

    private interface CheckedAction { void run() throws Exception; }
}
