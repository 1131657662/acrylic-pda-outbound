package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;

/** Synthetic pinned registration and framed controlled log; no real enrollment or original identity claim. */
final class RecoveryTemporaryEvidenceTrustFixture {
    final RecoveryDeviceRegistrationFixture registration=new RecoveryDeviceRegistrationFixture();
    final RecoveryControlledPermissionFixture history=new RecoveryControlledPermissionFixture();
    final RecoverySourcePermission.Proof permission=registration.nativeSource.initial.state.permissionReader().verify(registration.nativeSource.initial.source());
    final RecoveryDeviceRegistrationCapture.Database database=new RecoveryDeviceRegistrationCapture.Database(
        registration.mysqlServer,history.logged.schema,"1");

    RecoveryTemporaryEvidenceTrustFixture() throws Exception {
        registration.value.put("databaseSchema",database.schema()); registration.publish();
        var id=registration.source.identity; var window=permission.windows().getFirst();
        for(var boundary:List.of(history.logged.begin,history.logged.end)) {
            boundary.put("mysqlServerUuid",database.mysqlServerUuid().toString()).put("userId",id.operatorId().toString())
                .put("deviceId",id.deviceId().toString());
            var effective=(ObjectNode)boundary.path("effective");
            effective.put("warehouseId",id.warehouseId().toString()).put("userAuthVersion",window.userAuthVersion())
                .put("deviceVersion",window.deviceVersion());
            var permissions=effective.putArray("permissions"); window.permissions().stream().sorted().forEach(permissions::add);
            var warehouses=effective.putArray("warehouseIds"); window.warehouses().forEach(value -> warehouses.add(value.toString()));
        }
        history.logged.begin.put("observedAt",window.from().toString()); history.logged.end.put("observedAt",window.through().toString());
        history.logged.micros=window.from().plusSeconds(1).getEpochSecond()*1_000_000;
        history.logged.replaceTransaction("submission"); history.publish();
    }

    RecoveryDeviceRegistration.Proof root() {
        var id=registration.source.identity;
        var expected=new RecoveryDeviceRegistration.Expected(database.mysqlServerUuid(),database.schema(),"1",id.warehouseId(),id.deviceId(),
            Instant.parse("2026-09-20T00:00:00Z"));
        return registration.reader().read(new RecoveryDeviceRegistration.Request(registration.materials,registration.pin,expected));
    }
    RecoveryPermissionContinuity.Request log() {
        var raw=history.logged.request(); var id=registration.source.identity;
        var expected=new RecoveryPermissionBoundary.Request(database,id.operatorId(),id.deviceId());
        return new RecoveryPermissionContinuity.Request(expected,raw.begin(),raw.beginSha256(),raw.end(),raw.endSha256(),raw.binlog(),raw.binlogSha256());
    }
    void verify() {
        RecoveryTemporaryEvidenceTrust.verify(registration.nativeSource.initial.source(),root(),registration.nativeSource.evidence.request(),permission,List.of(log()),database);
    }
}
