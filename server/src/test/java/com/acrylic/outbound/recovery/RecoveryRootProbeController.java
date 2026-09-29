package com.acrylic.outbound.recovery;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Callable;
import javax.sql.DataSource;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Real-HTTP probe endpoints for the S3 run: one session write, one error dispatch and one async
 * dispatch, each recording what the operation root looked like while the request was being served.
 * Test-side only: it lives behind the {@code s3-probe} profile, which no other context activates,
 * so a component scan of the recovery package never registers it.
 */
@Profile("s3-probe")
@RestController
final class RecoveryRootProbeController {
    private final RecoveryOperationRegistry operations;
    private final RecoveryRootObservation observation;
    private final DataSource source;

    RecoveryRootProbeController(RecoveryOperationRegistry operations, RecoveryRootObservation observation, DataSource source) {
        this.operations = operations;
        this.observation = observation;
        this.source = source;
    }

    @GetMapping("/__s3/session")
    String session(HttpServletRequest request) {
        request.getSession(true).setAttribute("s3-session", UUID.randomUUID().toString());
        observation.duringRequest("/__s3/session", operations.liveRoots());
        return "session-ok";
    }

    @GetMapping("/__s3/error")
    String error(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.getSession(true).setAttribute("s3-error", UUID.randomUUID().toString());
        observation.duringRequest("/__s3/error", operations.liveRoots());
        response.sendError(500, "s3-probe-error");
        return "unreachable";
    }

    /**
     * The async work runs on a thread that no dispatch bound; it must not be able to borrow. The
     * container then performs the ASYNC dispatch, which binds its own thread to the same root.
     */
    @GetMapping("/__s3/async")
    Callable<String> async() {
        return () -> {
            observation.asyncBorrow(borrow());
            observation.duringRequest("/__s3/async", operations.liveRoots());
            return "async-ok";
        };
    }

    private String borrow() {
        try (var connection = source.getConnection()) {
            return "ADMITTED:" + (connection != null);
        } catch (RuntimeException | java.sql.SQLException error) {
            return error.getMessage();
        }
    }
}
