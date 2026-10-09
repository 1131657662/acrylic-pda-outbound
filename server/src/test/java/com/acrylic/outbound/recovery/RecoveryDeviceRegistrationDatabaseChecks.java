package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** Actual isolated database reads; synthetic identity and development public key, not original device proof. */
public final class RecoveryDeviceRegistrationDatabaseChecks {
    public static void verify(JdbcTemplate jdbc, Path attempt) throws Exception {
        var f = new RecoveryDeviceRegistrationFixture(); var id = f.source.identity;
        seed(jdbc, f); var capture = new RecoveryDeviceRegistrationCapture(jdbc.getDataSource());
        var database = new RecoveryDeviceRegistrationCapture.Database(UUID.fromString(jdbc.queryForObject("SELECT @@server_uuid", String.class)),
            jdbc.queryForObject("SELECT DATABASE()", String.class), jdbc.queryForObject("SELECT recovery_epoch FROM server_state WHERE id=1", String.class));
        var request = new RecoveryDeviceRegistrationCapture.Request(database, id.warehouseId(), id.deviceId());
        var snapshots = new E2aMigrationSnapshot(jdbc, attempt, attempt.resolve("storage"));
        var before = snapshots.capture();
        var direct = capture.capture(request); consume(f, direct, request, attempt.resolve("registration-direct.json"));
        refusals(jdbc, capture, request);
        assertThat(snapshots.capture()).isEqualTo(before);
        f.paired(); paired(jdbc, f);
        var pairedBefore = snapshots.capture(); var paired = capture.capture(request);
        consume(f, paired, request, attempt.resolve("registration-paired.json"));
        assertThat(snapshots.capture()).isEqualTo(pairedBefore);
        Files.writeString(attempt.resolve("registration-before.json"), before.toPrettyString());
        Files.writeString(attempt.resolve("registration-paired-before.json"), pairedBefore.toPrettyString());
        Files.writeString(attempt.resolve("registration-after.json"), snapshots.capture().toPrettyString());
        Files.writeString(attempt.resolve("registration-checks.txt"), "PASS actual MySQL time/instance/direct+paired read-only captures/independent pin reads/unchanged tables+files/refusals; synthetic identity+control, development P256; no native business recovery\n");
    }

    private static void seed(JdbcTemplate jdbc, RecoveryDeviceRegistrationFixture f) {
        var id = f.source.identity;
        UUID creator = UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM app_user ORDER BY id LIMIT 1", String.class));
        jdbc.update("INSERT INTO warehouse(id,code,name,status) VALUES(?,?,?,'ACTIVE')",
            Ids.bytes(id.warehouseId()), "KEY-" + id.warehouseId().toString().substring(0,8), "开发登记原仓");
        jdbc.update("INSERT INTO device(id,warehouse_id,device_code,name,public_key,key_sha256,status,row_version,created_by,created_at,"
            + "temporary_order_enabled) VALUES(?,?,?,?,?,?,'ACTIVE',7,?,UTC_TIMESTAMP(3),TRUE)", Ids.bytes(id.deviceId()),
            Ids.bytes(id.warehouseId()), "KEY-" + id.deviceId().toString().substring(0,8), "开发登记设备",
            f.device().path("publicKey").asText(), f.device().path("keyTextSha256").asText(), Ids.bytes(creator));
    }

    private static void paired(JdbcTemplate jdbc, RecoveryDeviceRegistrationFixture f) {
        var row = f.pairing(); var approval = row.path("approval");
        UUID approver = UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM app_user ORDER BY id LIMIT 1", String.class));
        jdbc.update("INSERT INTO device_pairing(id,public_key,key_sha256,credential_sha256,request_sha256,model,short_code,status,"
            + "created_at,expires_at,recovery_epoch,approval_json,approval_sha256,approved_by,approved_auth_version,device_id) "
            + "VALUES(?,?,?,?,?,?,'ABCD1234','COMPLETED',UTC_TIMESTAMP(3),TIMESTAMPADD(MINUTE,10,UTC_TIMESTAMP(3)),1,?,?,?,?,?)",
            Ids.bytes(UUID.fromString(row.path("applicationId").asText())), row.path("publicKey").asText(), row.path("keyTextSha256").asText(),
            DeviceCrypto.sha("development credential"), DeviceCrypto.sha("development request"), "development", approval.toString(),
            row.path("approvalSha256").asText(), Ids.bytes(approver), 3, Ids.bytes(f.source.identity.deviceId()));
    }

    private static void consume(RecoveryDeviceRegistrationFixture f, RecoveryDeviceRegistrationCapture.Observation observation,
            RecoveryDeviceRegistrationCapture.Request request, Path receipt) throws Exception {
        byte[] bytes = observation.originalBytes();
        Files.write(receipt, bytes, StandardOpenOption.CREATE_NEW);
        Files.write(f.materials.resolve(observation.originalSha256() + ".json"), bytes, StandardOpenOption.CREATE_NEW);
        var db = request.database();
        var expected = new RecoveryDeviceRegistration.Expected(db.mysqlServerUuid(), db.schema(), db.sourceRecoveryEpoch(),
            request.warehouseId(), request.deviceId(), observation.observedAt());
        var proof = f.reader().read(new RecoveryDeviceRegistration.Request(f.materials, observation.originalSha256(), expected));
        assertThat(proof.trustedKey().getEncoded()).containsExactly(f.nativeSource.evidence.key.getPublic().getEncoded());
        assertThat(proof.ordinaryWritesAllowed()).isFalse(); assertThat(proof.localDeletionAllowed()).isFalse();
        bytes[0] ^= 1; assertThat(observation.originalBytes()).isNotEqualTo(bytes);
        Files.writeString(receipt.resolveSibling(receipt.getFileName() + ".pin"), observation.originalSha256(), StandardOpenOption.CREATE_NEW);
        Files.writeString(receipt.resolveSibling(receipt.getFileName() + ".material-root.txt"), f.materials.toString(), StandardOpenOption.CREATE_NEW);
        assertThat(observation.observedAt()).isBeforeOrEqualTo(Instant.now().plusSeconds(5));
    }

    private static void refusals(JdbcTemplate jdbc, RecoveryDeviceRegistrationCapture capture,
            RecoveryDeviceRegistrationCapture.Request request) {
        var db = request.database();
        var wrong = new RecoveryDeviceRegistrationCapture.Database(UUID.randomUUID(), db.schema(), db.sourceRecoveryEpoch());
        assertThatThrownBy(() -> capture.capture(new RecoveryDeviceRegistrationCapture.Request(wrong, request.warehouseId(), request.deviceId())))
            .hasMessage("RECOVERY_DEVICE_CAPTURE_DATABASE");
        assertThatThrownBy(() -> capture.capture(new RecoveryDeviceRegistrationCapture.Request(db, request.warehouseId(), UUID.randomUUID())))
            .hasMessage("RECOVERY_DEVICE_CAPTURE_UNKNOWN");
        assertThatThrownBy(() -> capture.capture(new RecoveryDeviceRegistrationCapture.Request(db, UUID.randomUUID(), request.deviceId())))
            .hasMessage("RECOVERY_DEVICE_REGISTRATION_IDENTITY");
        new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())).execute(status -> {
            assertThatThrownBy(() -> capture.capture(request)).hasMessage("RECOVERY_DEVICE_CAPTURE_TRANSACTION"); return null;
        });
        jdbc.update("UPDATE server_state SET service_mode='RECOVERY_PENDING' WHERE id=1");
        try { assertThatThrownBy(() -> capture.capture(request)).hasMessage("RECOVERY_DEVICE_CAPTURE_DATABASE"); }
        finally { jdbc.update("UPDATE server_state SET service_mode='NORMAL' WHERE id=1"); }
    }
}
