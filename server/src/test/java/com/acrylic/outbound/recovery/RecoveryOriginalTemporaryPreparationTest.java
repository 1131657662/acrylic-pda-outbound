package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

class RecoveryOriginalTemporaryPreparationTest {
    private static final class Fixture {
        final RecoveryTemporaryActivationFixture nativeSource = new RecoveryTemporaryActivationFixture();
        final RecoverySourceFixture source = nativeSource.baseline.prep.state.authority.source;
        final RecoveryDeviceRegistrationCapture.Database database = new RecoveryDeviceRegistrationCapture.Database(UUID.randomUUID(), "original_temp", "1");
        final ObjectNode metadata;
        final Instant observed = Instant.parse("2026-09-23T01:00:00Z");
        String pin;
        Fixture() throws Exception {
            metadata = SubmissionJson.object().put("formatVersion", 1).put("observationKind", "ORIGINAL_TEMP_PREPARATION")
                .put("mysqlServerUuid", database.mysqlServerUuid().toString()).put("databaseSchema", database.schema())
                .put("sourceRecoveryEpoch", "1").put("observedAt", observed.toString()).put("originSha256", sha256(source.read().original("origin")));
            metadata.set("source", RecoveryOriginalOccupancyFacts.source(source.identity)); publish();
        }
        void publish() throws Exception {
            byte[] bytes = RecoveryControlJson.encode(metadata); pin = sha256(bytes);
            Files.write(source.materials.resolve(pin + ".json"), bytes);
        }
        RecoveryOriginalTemporaryPreparation reader() {
            return new RecoveryOriginalTemporaryPreparation(source.control.location(), source.control.checkpoint,
                new RecoveryReceiptKeys(List.of(nativeSource.baseline.prep.state.authority.key.pair.getPublic().getEncoded())));
        }
        RecoveryOriginalTemporaryPreparation.Request request() {
            return new RecoveryOriginalTemporaryPreparation.Request(source.materials, pin, database, observed);
        }
    }

    @Test void consumesOriginalPinnedPreparationAndNanosecondAckCommand() throws Exception {
        var f = new Fixture(); byte[] bytes = f.source.read().original("origin");
        var proof = f.reader().read(f.request(), f.source.read());
        assertThat(proof.activation().activatedAt()).isEqualTo(Instant.parse("2026-09-20T00:01:00.123456789Z"));
        assertThat(proof.activation().baseline().preparation().partHashes()).hasSize(5);
        assertThat(proof.ordinaryWritesAllowed()).isFalse(); assertThat(proof.localDeletionAllowed()).isFalse();
        assertThat(f.source.read().original("origin")).containsExactly(bytes);
    }

    @ParameterizedTest @ValueSource(strings={"mysqlServerUuid", "databaseSchema", "sourceRecoveryEpoch", "observedAt", "observationKind", "originSha256"})
    void refusesRepinnedWrongOriginalContext(String field) throws Exception {
        var f = new Fixture();
        String wrong = switch(field) {
            case "mysqlServerUuid" -> UUID.randomUUID().toString(); case "sourceRecoveryEpoch" -> "2";
            case "observedAt" -> "2026-09-23T01:00:00.001Z"; case "originSha256" -> "a".repeat(64); default -> "changed";
        };
        f.metadata.put(field, wrong); f.publish();
        assertThatThrownBy(() -> f.reader().read(f.request(), f.source.read())).isInstanceOf(RecoveryControlException.class);
    }

    @Test void refusesOriginalCommandReconstructedAtMillisecondPrecision() throws Exception {
        var f = new Fixture(); var original = f.source.read();
        f.nativeSource.command.put("activatedAt", "2026-09-20T00:01:00.123Z"); f.nativeSource.write();
        f.metadata.put("originSha256", sha256(f.source.read().original("origin"))); f.publish();
        assertThatThrownBy(() -> f.reader().read(f.request(), f.source.read())).hasMessage("RECOVERY_TEMP_ACTIVATION_DIGEST");
        assertThat(original.original("origin")).isNotEqualTo(f.source.read().original("origin"));
    }

    @Test void refusesSourceWatermarkSubstitution() throws Exception {
        var f = new Fixture(); ((ObjectNode)f.metadata.path("source")).put("captureHighWatermark", "8"); f.publish();
        assertThatThrownBy(() -> f.reader().read(f.request(), f.source.read())).hasMessage("RECOVERY_TEMP_ORIGINAL_SOURCE");
    }

    @Test void refusesChangedFileUnderOriginalIndependentPin() throws Exception {
        var f = new Fixture(); f.metadata.put("observedAt", "2026-09-23T00:59:00Z");
        Files.write(f.source.materials.resolve(f.pin + ".json"), RecoveryControlJson.encode(f.metadata));
        assertThatThrownBy(() -> f.reader().read(f.request(), f.source.read())).hasMessage("RECOVERY_TEMP_ORIGINAL_PIN_MISMATCH");
    }

    @Test void refusesMaterialsWithinRecoveryAuthority() throws Exception {
        var f = new Fixture(); var request = new RecoveryOriginalTemporaryPreparation.Request(f.source.control.root, f.pin, f.database, f.observed);
        assertThatThrownBy(() -> f.reader().read(request, f.source.read())).hasMessage("RECOVERY_SOURCE_INSIDE_AUTHORITY");
    }

    @ParameterizedTest @ValueSource(strings={"missingPart", "changedPart", "missingAck", "changedSignedAck"})
    void refusesRepinnedIncompleteOrDamagedNativeMaterials(String change) throws Exception {
        var f = new Fixture(); var prep = f.nativeSource.baseline.prep;
        if (change.equals("missingPart")) ((ObjectNode)prep.origin.path("parts")).remove("inventory");
        else if (change.equals("changedPart")) ((ObjectNode)prep.origin.path("parts")).put("inventory", "AAAA");
        else if (change.equals("missingAck")) prep.origin.remove("activation");
        else {
            var receipt = SubmissionJson.parse(prep.origin.at("/activation/receipt").asText());
            ((ObjectNode)receipt).put("signedAck", "invalid.signature.bytes");
            ((ObjectNode)prep.origin.path("activation")).put("receipt", receipt.toString());
        }
        prep.write(); f.metadata.put("originSha256", sha256(f.source.read().original("origin"))); f.publish();
        assertThatThrownBy(() -> f.reader().read(f.request(), f.source.read())).isInstanceOf(RecoveryControlException.class);
    }
}
