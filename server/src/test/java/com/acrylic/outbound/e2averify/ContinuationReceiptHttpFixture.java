package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.OutboundApplication;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual loopback HTTP/authentication over explicitly synthetic stopped-source facts. */
final class ContinuationReceiptHttpFixture implements AutoCloseable {
    final RetentionDatabaseFixture database;
    final ServletWebServerApplicationContext app;
    final String token;
    final String otherToken;
    final String otherDeviceToken;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    ContinuationReceiptHttpFixture() throws Exception {
        this("65");
    }
    ContinuationReceiptHttpFixture(String migrationTarget) throws Exception {
        database = new RetentionDatabaseFixture(); database.profile.migrate(database.jdbc, migrationTarget);
        database.jdbc.update("INSERT INTO order_submission_slot(order_id,scope_id,work_round,current_submission_id,phase) "
            + "VALUES(?,?,1,?,'FROZEN')", Ids.bytes(database.id("orderId")), Ids.bytes(database.id("mappedScopeId")),
            Ids.bytes(database.id("mappedSubmissionId")));
        database.tx.executeWithoutResult(status -> database.apply(database.verified(), java.util.Collections.nCopies(3, "RETAIN")));
        token = session(database.id("operatorId")); otherToken = session(database.id("mismatchUserId"));
        otherDeviceToken = otherDeviceSession();
        E2aCrossSourceHttpSetup.signing(database.profile.attempt);
        Files.createDirectory(database.profile.attempt.resolve("http"));
        app = start();
    }

    private String session(UUID user) {
        var jdbc = database.jdbc;
        jdbc.update("INSERT IGNORE INTO user_warehouse VALUES(?,?)", Ids.bytes(user), Ids.bytes(database.id("warehouseId")));
        jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code IN ('PDA_OPERATOR','SUPERVISOR','WAREHOUSE_ADMIN')", Ids.bytes(user));
        return sessionOnDevice(user, database.id("deviceId"));
    }

    private String sessionOnDevice(UUID user, UUID device) {
        String access = DeviceCrypto.secret();
        var jdbc = database.jdbc;
        jdbc.update("""
            INSERT INTO pda_session(id,device_id,user_id,user_auth_version,device_version,access_hash,refresh_hash,
              access_expires_at,expires_at,created_at)
            VALUES(?,?,?,1,1,?,?,DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 1 DAY),
              DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 2 DAY),UTC_TIMESTAMP(3))
            """, Ids.bytes(UUID.randomUUID()), Ids.bytes(device), Ids.bytes(user),
            DeviceCrypto.sha(access), DeviceCrypto.sha(DeviceCrypto.secret()));
        return access;
    }

    private String otherDeviceSession() {
        UUID device = UUID.randomUUID();
        String key = "synthetic-" + device;
        database.jdbc.update("""
            INSERT INTO device(id,warehouse_id,device_code,name,public_key,key_sha256,status,row_version,created_by,created_at)
            SELECT ?,warehouse_id,?,'Synthetic other device',?,?,'ACTIVE',1,created_by,UTC_TIMESTAMP(3)
            FROM device WHERE id=?
            """, Ids.bytes(device), "OTHER-" + device.toString().substring(0, 8), key, DeviceCrypto.sha(key), Ids.bytes(database.id("deviceId")));
        return sessionOnDevice(database.id("operatorId"), device);
    }

    private ServletWebServerApplicationContext start() {
        var profile = database.profile;
        return (ServletWebServerApplicationContext) new SpringApplication(OutboundApplication.class).run(
            "--server.address=127.0.0.1", "--server.port=0", "--spring.flyway.enabled=false",
            "--spring.datasource.url=jdbc:mysql://127.0.0.1:19331/" + profile.database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
            "--spring.datasource.username=b4_retention_" + System.getenv("B4_RETENTION_RUN"),
            "--spring.datasource.password=" + System.getenv("B4_RETENTION_PASSWORD"),
            "--outbound.storage=" + profile.attempt.resolve("storage"),
            "--outbound.signing.private-key=" + profile.attempt.resolve("private/server-private.der"),
            "--outbound.signing.public-key=" + profile.attempt.resolve("private/server-public.der"),
            "--outbound.worker.enabled=false", "--outbound.bootstrap.enabled=false",
            "--spring.session.jdbc.cleanup-cron=-", "--outbound.temporary.commands.enabled=true");
    }

    HttpResponse<byte[]> post(JsonNode command, String access, String label) throws Exception {
        return send(new Call(command, access, "/api/v1/pda/temporary-commands", label));
    }

    HttpResponse<byte[]> submit(JsonNode command, String label) throws Exception {
        return send(new Call(command, token, "/api/v1/pda/temporary-orders/submissions", label));
    }

    HttpResponse<byte[]> abort(JsonNode command, String label) throws Exception {
        return send(new Call(command, token, "/api/v1/pda/submissions/" + command.path("submissionId").asText() + "/abort", label));
    }

    private HttpResponse<byte[]> send(Call call) throws Exception {
        var command = call.command(); var label = call.label();
        byte[] body = SubmissionJson.canonical(command).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(database.profile.attempt.resolve("http/" + label + "-request.json"), body, StandardOpenOption.CREATE_NEW);
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.getWebServer().getPort()
                + call.route())).timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer " + call.access()).header("Content-Type", "application/json")
            .header("X-Server-Recovery-Epoch", "1")
            .header("X-Request-Id", label).POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        Files.write(database.profile.attempt.resolve("http/" + label + "-response.json"), response.body(), StandardOpenOption.CREATE_NEW);
        Files.writeString(database.profile.attempt.resolve("http/" + label + "-status.txt"), Integer.toString(response.statusCode()));
        return response;
    }

    byte[] accepted(JsonNode command, String label) throws Exception {
        var response = post(command, token, label);
        assertThat(response.statusCode()).as(label + ": " + new String(response.body())).isEqualTo(200);
        var receipt = SubmissionJson.parse(response.body());
        assertThat(receipt.path("capabilities")).isEqualTo(com.acrylic.outbound.temporary.command.CommandReceiptCodec.capabilities());
        return response.body();
    }

    void completed(JsonNode activation) {
        var decision = activation.path("businessDecision"); UUID target = UUID.randomUUID();
        UUID ticket = UUID.fromString(decision.path("ticketId").asText());
        UUID authorization = UUID.fromString(decision.path("newAuthorizationId").asText());
        String receipt = "{\"status\":\"SUCCEEDED\"}";
        var jdbc = app.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        var tx = new org.springframework.transaction.support.TransactionTemplate(
            app.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        tx.executeWithoutResult(status -> {
            jdbc.update("""
                INSERT INTO submission(id,scope_id,assignment_id,temporary_authorization_id,freeze_device_id,operator_id,
                  work_round,source_recovery_epoch,status,receipt_text,created_at,updated_at)
                VALUES(?,?,NULL,?,?,?,2,1,'SUCCEEDED',?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
                """, Ids.bytes(target), Ids.bytes(database.id("mappedScopeId")), Ids.bytes(authorization),
                Ids.bytes(database.id("deviceId")), Ids.bytes(database.id("operatorId")), receipt);
            jdbc.update("UPDATE submission_scope SET work_round=2,current_submission_id=? WHERE id=?",
                Ids.bytes(target), Ids.bytes(database.id("mappedScopeId")));
            jdbc.update("""
                UPDATE bale_commitment SET state='DISPATCHED',submission_id=?,order_id=?,
                  temporary_continuation_id=NULL,source_evidence_set_id=NULL
                WHERE temporary_continuation_id=? AND state='TEMP_CONTINUATION_RESERVED'
                """, Ids.bytes(target), Ids.bytes(database.id("orderId")), Ids.bytes(ticket));
            var store = app.getBean(com.acrylic.outbound.temporary.command.TemporaryContinuationLifecycleStore.class);
            store.complete(new com.acrylic.outbound.temporary.command.TemporaryContinuationLifecycleStore.Completion(
                ticket, database.id("mappedScopeId"), 1, authorization, target, UUID.randomUUID(), receipt, java.time.Instant.now()));
        });
    }

    @Override public void close() { app.close(); }
    private record Call(JsonNode command, String access, String route, String label) { }
}
