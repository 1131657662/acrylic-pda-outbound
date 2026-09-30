package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.OutboundApplication;
import com.acrylic.outbound.auth.SessionUser;
import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.correction.CorrectionCommandService;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;

/**
 * A2.1 ruling B (2026-09-30) — the correction approval gate is <b>kind-aware</b>: an approval command
 * ({@code APPROVE}/{@code WITHDRAW}) is authorised by {@code correction:approve} alone, every other command still
 * needs {@code outbound:correct}; and maker-checker refuses the body that created the document.
 *
 * <p>This IT runs against an <b>already migrated database that it does not own</b> (no provision, no seed, no
 * migration), because the ruling requires the cheap verification path: {@code A21_GATE_VERIFY=true} plus
 * {@code OUTBOUND_DB_*} pointing at a reused isolated schema. Everything it asserts is either a seeded authority
 * fact or the refusal code the real service returns for a real actor.
 */
@EnabledIfEnvironmentVariable(named = "A21_GATE_VERIFY", matches = "true")
class E2aCorrectionApprovalGateIT {
    private static final String SUPERVISOR = "SUPERVISOR";
    private static final String ADMIN = "WAREHOUSE_ADMIN";
    private static final String CORRECT = "outbound:correct";
    private static final String APPROVE = "correction:approve";
    private static final String SERVICE = "A2.1 进门码分离验收";

    @Test void approvalBodyIsKindAwareAndTheCreatorCannotApprove() throws Exception {
        String url = System.getenv("OUTBOUND_DB_URL");
        String user = System.getenv("OUTBOUND_DB_USER");
        String password = System.getenv("OUTBOUND_DB_PASSWORD");
        assertThat(url).as("reused schema URL").isNotBlank();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        var notes = new StringBuilder("verification level=real service on a reused migrated schema (no provision)\n");

        Path evidence = Path.of(".local", "a21-gate-evidence.txt").toAbsolutePath();
        Files.createDirectories(evidence.getParent());

        var app = start(url, user, password);
        try {
            verify(jdbc, app.getBean(CorrectionCommandService.class), notes);
        } finally {
            app.close();
        }
        Files.writeString(evidence, notes.toString());
    }

    private static ConfigurableApplicationContext start(String url, String user, String password) {
        Path keys = Path.of(".local", "it-keys").toAbsolutePath();
        Path storage = Path.of(".local", "a21-gate-storage").toAbsolutePath();
        return new SpringApplication(OutboundApplication.class).run(
            "--server.address=127.0.0.1", "--server.port=19434",
            "--spring.flyway.enabled=false",
            "--spring.datasource.url=" + url, "--spring.datasource.username=" + user,
            "--spring.datasource.password=" + password,
            "--outbound.storage=" + storage,
            "--outbound.signing.private-key=" + keys.resolve("server-private.der"),
            "--outbound.signing.public-key=" + keys.resolve("server-public.der"),
            // The correction operation beans only exist while the command surface is enabled; without this the
            // APPROVE kind is CORRECTION_COMMAND_NOT_OPEN and the gate would never be reached.
            "--outbound.corrections.commands.enabled=true",
            "--outbound.worker.enabled=false", "--outbound.bootstrap.enabled=false",
            "--spring.session.jdbc.cleanup-cron=-");
    }

    private void verify(JdbcTemplate jdbc, CorrectionCommandService service, StringBuilder notes) throws Exception {
        // ---- seeded authority facts (the premise of every counterexample below) --------------------------------
        var supervisorBodies = roleCodes(jdbc, SUPERVISOR);
        var adminBodies = roleCodes(jdbc, ADMIN);
        notes.append("SUPERVISOR=").append(supervisorBodies).append('\n');
        notes.append("WAREHOUSE_ADMIN holds ").append(adminBodies.size()).append(" code(s)\n");
        assertThat(supervisorBodies).as("SUPERVISOR holds the approval body").contains(APPROVE);
        assertThat(supervisorBodies).as("and does NOT hold the document-creation body (counterexample 2 premise)")
            .doesNotContain(CORRECT);
        assertThat(adminBodies).as("WAREHOUSE_ADMIN still holds the creation body").contains(CORRECT);
        assertThat(adminBodies).as("and does NOT hold the approval body (counterexample 1 premise)")
            .doesNotContain(APPROVE);

        var facts = jdbc.queryForMap("""
            SELECT BIN_TO_UUID(o.id) orderId,BIN_TO_UUID(o.warehouse_id) warehouseId,
              CAST(o.current_result_version AS CHAR) version
            FROM outbound_order o JOIN order_result_version v
              ON v.order_id=o.id AND v.result_version=o.current_result_version LIMIT 1
            """);
        UUID warehouse = UUID.fromString(String.valueOf(facts.get("warehouseId")));
        UUID order = UUID.fromString(String.valueOf(facts.get("orderId")));
        long version = Long.parseLong(String.valueOf(facts.get("version")));
        var supervisor = user(jdbc, warehouse, SUPERVISOR, "gate-sup-");
        var admin = user(jdbc, warehouse, ADMIN, "gate-adm-");

        // Two documents on the same order: one created by the admin (counterexamples 1 and 2), one created by the
        // supervisor itself (counterexample 3). The maker-checker rule is about the recorded requester, so the rows
        // are inserted directly: no CREATE command is needed to establish who the document belongs to.
        UUID byAdmin = document(jdbc, warehouse, order, version, admin);
        UUID bySupervisor = document(jdbc, warehouse, order, version, supervisor);

        // ---- counterexample 1: a non-supervisor is refused by the approval gate --------------------------------
        String adminOutcome = approve(service, warehouse, byAdmin, admin, jdbc);
        notes.append("①WAREHOUSE_ADMIN(correct-only) 批单 -> ").append(orNull(adminOutcome)).append('\n');
        assertThat(adminOutcome).as("correct-only actor must be refused by the approval gate")
            .isEqualTo("CORRECTION_PERMISSION_DENIED");

        // ---- counterexample 2: the supervisor, without outbound:correct, gets past the gate --------------------
        String supervisorOutcome = approve(service, warehouse, byAdmin, supervisor, jdbc);
        notes.append("②SUPERVISOR(approve-only) 批单 -> ").append(orNull(supervisorOutcome))
            .append(" (期望：不是权限拒绝)\n");
        assertThat(supervisorOutcome).as("approve-only actor must not be refused on permission")
            .isNotEqualTo("CORRECTION_PERMISSION_DENIED");

        // ---- counterexample 3: maker-checker — the creator may not approve their own document -----------------
        String selfOutcome = approve(service, warehouse, bySupervisor, supervisor, jdbc);
        notes.append("③SUPERVISOR 批自己建立的单 -> ").append(orNull(selfOutcome))
            .append(" (期望 CORRECTION_SELF_APPROVAL_DENIED)\n");
        assertThat(selfOutcome).as("the creating body may not approve its own document")
            .isEqualTo("CORRECTION_SELF_APPROVAL_DENIED");
    }

    private static String orNull(String outcome) { return outcome == null ? "ACCEPTED" : outcome; }

    private static String approve(CorrectionCommandService service, UUID warehouse, UUID document, Actor actor,
        JdbcTemplate jdbc) throws Exception {
        var command = (ObjectNode) SubmissionJson.object().put("commandVersion", 1).put("commandType", "APPROVE")
            .put("commandId", UUID.randomUUID().toString()).put("transportRecoveryEpoch", "1")
            .put("warehouseId", warehouse.toString()).put("documentId", document.toString()).put("reason", SERVICE);
        var expected = SubmissionJson.object();
        for (String key : List.of("documentId", "documentSha256", "status", "expectedResultVersion"))
            expected.set(key, documentFacts(jdbc, document).get(key));
        expected.put("userAuthVersion", "1");
        command.set("expected", expected);
        var identity = new SessionUser(actor.id(), actor.username(), SERVICE, "x", 1, true);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(identity, "unused", List.of()));
        try {
            service.execute(SubmissionJson.canonical(command).getBytes(StandardCharsets.UTF_8), "gate-check");
            return null;
        } catch (BusinessException refused) {
            return refused.code();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static JsonNode documentFacts(JdbcTemplate jdbc, UUID document) {
        return SubmissionJson.parse(jdbc.queryForObject("""
            SELECT JSON_OBJECT('documentId',BIN_TO_UUID(id),'documentSha256',document_sha256,'status',status,
              'expectedResultVersion',CAST(expected_result_version AS CHAR))
            FROM correction_document WHERE id=?
            """, String.class, Ids.bytes(document)));
    }

    private static UUID document(JdbcTemplate jdbc, UUID warehouse, UUID order, long version, Actor requester) {
        UUID id = Ids.next();
        jdbc.update("INSERT INTO correction_document(id,warehouse_id,order_id,type,expected_result_version,reason,"
            + " document_sha256,status,source_recovery_epoch,requested_by,requested_at)"
            + " VALUES(?,?,?,'WEIGHT_CORRECTION',?,?,?,'SUBMITTED',1,?,UTC_TIMESTAMP(3))",
            Ids.bytes(id), Ids.bytes(warehouse), Ids.bytes(order), version, SERVICE,
            "%064x".formatted(id.getLeastSignificantBits() & Long.MAX_VALUE), Ids.bytes(requester.id()));
        return id;
    }

    private static List<String> roleCodes(JdbcTemplate jdbc, String role) {
        return jdbc.queryForList("SELECT p.code FROM role_permission rp JOIN role r ON r.id=rp.role_id"
            + " JOIN permission p ON p.id=rp.permission_id WHERE r.code=? ORDER BY p.code", String.class, role);
    }

    private record Actor(UUID id, String username) { }

    private static Actor user(JdbcTemplate jdbc, UUID warehouse, String role, String prefix) {
        UUID id = Ids.next();
        String username = prefix + id;
        jdbc.update("INSERT INTO app_user(id,username,display_name,password_hash,created_at)"
            + " VALUES(?,?,?,'x',UTC_TIMESTAMP(3))", Ids.bytes(id), username, SERVICE);
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)", Ids.bytes(id), Ids.bytes(warehouse));
        jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code=?", Ids.bytes(id), role);
        return new Actor(id, username);
    }
}
