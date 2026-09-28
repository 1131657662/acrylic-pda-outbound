package com.acrylic.c1probe;

import java.nio.file.Path;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.web.bind.annotation.*;
import com.acrylic.outbound.recovery.InitialNormalRegistration;
import com.acrylic.outbound.recovery.InitialNormalStore;
import com.acrylic.outbound.recovery.RecoveryAdmissionQueries;
import com.acrylic.outbound.recovery.RecoveryAdmissionVerifier;
import com.acrylic.outbound.recovery.RecoveryControlLocation;
import com.acrylic.outbound.recovery.RecoveryNormalRegistration;

/** Test-only controller outside the application scan; registered only by C1HttpProbe. */
@RestController
final class C1HttpController {
    private final DataSource source;
    private final InitialNormalStore store;
    private final Path closed;

    C1HttpController(DataSource source, Path base) {
        this.source = source; this.closed = base.resolve("closed-authority");
        this.store = new InitialNormalStore(new RecoveryControlLocation(base.resolve("initial-authority"),
            java.util.UUID.fromString("721e3f13-2841-4b09-b56c-0ea1e2201111"),
            List.of(base.resolve("data"), base.resolve("storage"))), base.resolve("trusted-original.json"));
    }

    @GetMapping("/__c1/verify") RecoveryAdmissionVerifier.Result verify() {
        return RecoveryAdmissionVerifier.verifyInitial(new RecoveryAdmissionQueries(source), store, closed);
    }

    @PostMapping("/__c1/register") InitialNormalRegistration.Result register() {
        return RecoveryNormalRegistration.registerInitial(source, store, closed);
    }
}
