package com.acrylic.outbound.recovery;

import java.nio.file.Path;
import java.util.UUID;

/**
 * A separate process that only takes the P5 root and starts: the cross-process half of the lease
 * publication/retirement evidence. Exit code 0 prints {@code STARTED}; a refusal prints its control
 * code and exits non-zero.
 */
public final class RecoveryLeaseStartupCheck {
    private RecoveryLeaseStartupCheck() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 7) throw new IllegalArgumentException("authority lease closed identity marker anchor installation");
        var location = new RecoveryControlLocation(Path.of(args[0]), UUID.fromString(args[6]),
            java.util.List.of(Path.of(args[0]).getParent().resolve("business")));
        var binding = new RecoveryLeaseRoots.Binding(location, Path.of(args[1]), Path.of(args[2]), args[3]);
        var roots = new RecoveryLeaseRoots(binding, args[4], RecoveryControlFormat.bytes(args[5]));
        var authority = new RecoveryFenceAuthority(roots);
        try {
            authority.validateStartup();
            authority.retire();
            System.out.println("STARTED");
        } catch (java.sql.SQLException | RuntimeException refused) {
            System.out.println(describe(refused));
            System.exit(1);
        }
    }

    private static String describe(Throwable failure) {
        var message = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            String detail = current.getMessage();
            if (detail != null && !message.toString().contains(detail)) message.append(detail).append(' ');
        }
        return message.toString().trim();
    }
}
