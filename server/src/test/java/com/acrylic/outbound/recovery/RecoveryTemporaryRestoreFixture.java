package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Independent metadata pin and original native bytes; actual DML is covered by the isolated target IT. */
final class RecoveryTemporaryRestoreFixture {
    final RecoveryTemporaryActivationFixture nativeSource = new RecoveryTemporaryActivationFixture();
    final RecoverySourceFixture source = nativeSource.baseline.prep.state.authority.source;
    final RecoveryReceiptKeys keys = new RecoveryReceiptKeys(List.of(nativeSource.baseline.prep.state.authority.key.pair.getPublic().getEncoded()));
    final RecoveryClosedTestSupply supply;
    final RecoveryTemporaryPreparationRestore.Request request;

    RecoveryTemporaryRestoreFixture() throws Exception {
        RecoveryClosedTestSupply.writers(source.control); supply = RecoveryClosedTestSupply.of(source);
        var database = new RecoveryDeviceRegistrationCapture.Database(UUID.randomUUID(),"original_temp","1");
        var observed = Instant.parse("2026-09-23T01:00:00Z");
        var metadata = SubmissionJson.object().put("formatVersion",1).put("observationKind","ORIGINAL_TEMP_PREPARATION")
            .put("mysqlServerUuid",database.mysqlServerUuid().toString()).put("databaseSchema",database.schema())
            .put("sourceRecoveryEpoch","1").put("observedAt",observed.toString()).put("originSha256",sha256(source.read().original("origin")));
        metadata.set("source",RecoveryOriginalOccupancyFacts.source(source.identity));
        byte[] bytes = RecoveryControlJson.encode(metadata); String pin = sha256(bytes);
        Files.write(source.materials.resolve(pin+".json"),bytes);
        request = new RecoveryTemporaryPreparationRestore.Request(source.materials,source.pin,
            new RecoveryOriginalTemporaryPreparation.Request(source.materials,pin,database,observed));
    }
}
