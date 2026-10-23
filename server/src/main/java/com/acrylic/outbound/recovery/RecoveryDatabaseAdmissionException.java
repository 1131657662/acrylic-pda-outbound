package com.acrylic.outbound.recovery;

import java.sql.SQLNonTransientConnectionException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

/** Exact closed-admission refusal; generic driver SQL states or messages never grant this identity. */
public final class RecoveryDatabaseAdmissionException extends SQLNonTransientConnectionException {
    static final String CODE = "RECOVERY_DATABASE_ADMISSION_REFUSED";

    RecoveryDatabaseAdmissionException(List<String> gaps) {
        super(CODE + ":" + String.join(",", gaps), "08004");
    }

    static boolean causedBy(Throwable failure) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (Throwable current = failure; current != null && seen.add(current); current = current.getCause()) {
            if (current instanceof RecoveryDatabaseAdmissionException) return true;
        }
        return false;
    }
}
