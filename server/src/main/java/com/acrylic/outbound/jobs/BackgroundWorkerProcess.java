package com.acrylic.outbound.jobs;

import java.util.UUID;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.*;

/** Same JAR, separate non-web process. A single scheduler executes one job at a time. */
@Configuration
@Profile("worker")
@ConditionalOnNotWebApplication
@ConditionalOnProperty(name = "outbound.worker.enabled", havingValue = "true")
@EnableScheduling
public class BackgroundWorkerProcess {
    private static final Logger LOG = LoggerFactory.getLogger(BackgroundWorkerProcess.class);
    private final BackgroundJobWorker worker;
    private final BackgroundJobStore jobs;
    private final String owner = "worker-" + UUID.randomUUID();

    public BackgroundWorkerProcess(BackgroundJobWorker worker, BackgroundJobStore jobs) { this.worker = worker; this.jobs = jobs; }

    @Scheduled(fixedDelayString = "${outbound.worker.poll-ms:1000}", initialDelayString = "${outbound.worker.poll-ms:1000}")
    public void poll() {
        try {
            worker.runOnce(owner).ifPresent(result -> LOG.info("Background job {}: {}", result.jobId(), result.outcome()));
        } catch (RuntimeException error) {
            // Never log raw SQL/connection/payload exceptions. The durable lease permits later recovery.
            LOG.warn("Background worker poll failed ({}); retrying on the next poll", error.getClass().getSimpleName());
        }
    }

    @Scheduled(fixedDelayString = "${outbound.worker.reminder-poll-ms:60000}", initialDelayString = "${outbound.worker.reminder-poll-ms:1000}")
    public void enqueueReviewReminderSweep() {
        try {
            long bucket = java.time.Instant.now().getEpochSecond() / 60;
            jobs.enqueue(new NewBackgroundJob("REVIEW_REMINDER_SWEEP", "review-reminder:" + bucket, "{}", 3, java.time.Instant.now()));
        } catch (RuntimeException error) {
            LOG.warn("Background reminder scheduling failed ({}); retrying on the next interval", error.getClass().getSimpleName());
        }
    }
}
