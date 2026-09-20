package com.acrylic.outbound.jobs;

import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
public class BackgroundJobWorker {
    private final BackgroundJobLeases leases;
    private final Map<String, BackgroundJobHandler> handlers;

    public BackgroundJobWorker(BackgroundJobLeases leases, List<BackgroundJobHandler> handlers) {
        this.leases = leases;
        this.handlers = handlers.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
            BackgroundJobHandler::jobType, handler -> handler));
    }

    @Transactional(propagation = Propagation.NEVER)
    public Optional<RunResult> runOnce(String owner) {
        leases.recoverExpired(handlers.keySet());
        return leases.claim(owner, handlers.keySet(), BackgroundJobExecution.LEASE).map(this::execute);
    }

    private RunResult execute(BackgroundJob job) {
        try {
            var result = handlers.get(job.jobType()).execute(new BackgroundJobExecution(job, leases));
            boolean completed = leases.complete(job, result);
            return new RunResult(job.id(), completed ? "SUCCEEDED" : "STALE");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return failed(job, new JobFailure("WORKER_INTERRUPTED", true));
        } catch (JobFailure error) {
            return failed(job, error);
        } catch (Exception error) {
            return failed(job, new JobFailure("HANDLER_FAILED", true));
        }
    }

    private RunResult failed(BackgroundJob job, JobFailure error) {
        return new RunResult(job.id(), leases.fail(job, error).name());
    }

    public record RunResult(UUID jobId, String outcome) { }
}
