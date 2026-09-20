package com.acrylic.outbound.jobs;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "outbound.worker.poll-ms=50")
@ActiveProfiles("worker")
@Import(BackgroundWorkerProcessIT.Handlers.class)
class BackgroundWorkerProcessIT {
    private static final String TYPE = "PROCESS." + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
    @Autowired BackgroundJobStore jobs;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationContext context;

    @Test void separateNonWebProfilePollsAndPublishesDurableResult() {
        try {
            var job = jobs.enqueue(new NewBackgroundJob(TYPE, TYPE, "{}", 3, Instant.EPOCH));
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(jobs.find(job.id()).orElseThrow().status()).isEqualTo(BackgroundJob.Status.SUCCEEDED);
                assertThat(jobs.findByDedupeKey(TYPE + ":result")).isPresent();
            });
            assertThat(context).isNotInstanceOf(org.springframework.boot.web.context.WebServerApplicationContext.class);
            assertThat(context.getBean(com.zaxxer.hikari.HikariDataSource.class).getMaximumPoolSize()).isEqualTo(2);
            assertThat(context.getBean(org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler.class).getPoolSize()).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM background_job WHERE job_type IN (?,?)", TYPE, TYPE + ".RESULT");
        }
    }

    @TestConfiguration
    static class Handlers {
        @Bean BackgroundJobHandler handler(BackgroundJobStore jobs) {
            return new BackgroundJobHandler() {
                public String jobType() { return TYPE; }
                public Runnable execute(BackgroundJobExecution execution) {
                    return () -> jobs.enqueue(new NewBackgroundJob(TYPE + ".RESULT", TYPE + ":result", "{}", 1, Instant.EPOCH));
                }
            };
        }
    }
}
