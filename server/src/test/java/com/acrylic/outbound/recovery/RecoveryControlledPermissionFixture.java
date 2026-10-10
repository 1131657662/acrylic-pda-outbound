package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Minimal supported constrained-account metadata. Real source locking and grants are checked separately on MySQL. */
final class RecoveryControlledPermissionFixture {
    final RecoveryPermissionLogFixture logged = new RecoveryPermissionLogFixture();
    final ObjectNode profile = SubmissionJson.object().put("currentAccount", "original_writer@127.0.0.1")
        .put("roleEdges", "0").put("mandatoryRoles", "").put("exclusiveCurrentSession", true)
        .put("eventScheduler", "OFF").put("replicationThreads", "0").put("sessionCannotDisableLogging", true);

    RecoveryControlledPermissionFixture() {
        var accounts = profile.putArray("accounts");
        accounts.addObject().put("user", "root").put("host", "localhost").put("locked", true);
        accounts.addObject().put("user", "original_writer").put("host", "127.0.0.1").put("locked", false);
        var grants = profile.putArray("grants"); String suffix = " TO `original_writer`@`127.0.0.1`";
        grants.add("GRANT RELOAD, SHUTDOWN, REPLICATION CLIENT ON *.*" + suffix);
        grants.add("GRANT SELECT, INSERT, UPDATE, DELETE ON `" + logged.schema + "`.*" + suffix);
        grants.add("GRANT SELECT ON `performance_schema`.*" + suffix);
        grants.add("GRANT SELECT (`Host`, `User`, `account_locked`) ON `mysql`.`user`" + suffix);
        grants.add("GRANT SELECT ON `mysql`.`role_edges`" + suffix); publish();
    }

    void publish() {
        for (var boundary : new ObjectNode[]{logged.begin, logged.end}) {
            boundary.put("observationKind", "ORIGINAL_CONTROLLED_PERMISSION_BOUNDARY");
            boundary.set("writerProfile", profile.deepCopy());
        }
    }

    RecoveryPermissionContinuity.Proof verify() { publish(); return RecoveryPermissionContinuity.verifyControlled(logged.request()); }
}
