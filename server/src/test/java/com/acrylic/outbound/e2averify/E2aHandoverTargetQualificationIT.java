package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.handover.HandoverCommandService;
import com.acrylic.outbound.handover.HandoverStore;
import com.acrylic.outbound.pda.AssignmentStore;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Exercises target requalification and issuance against a stopped, terminal original submission. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aHandoverTargetQualificationIT {
    @Test void targetEligibilityAndHighWatermarkHoldAcrossPreparationAndActivation() throws Exception {
        var profile = new E2aCrossSourceProfile();
        var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34");
        E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "58");
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var app = E2aCrossSourceHttpIT.start(profile, "--outbound.handover.commands.enabled=true")) {
            var f = new HandoverRollbackFixture(app);
            var store = app.getBean(HandoverStore.class);
            var commands = new HandoverSourceCommands(f, store);
            var calls = new HandoverRollbackCommands(f, store, app.getBean(HandoverCommandService.class));
            var request = SubmissionJson.parse(calls.call(commands.request(), f.requester));
            f.stopped(UUID.fromString(request.path("businessDecision").path("caseId").asText()));
            calls.call(commands.command("APPROVE"), f.approver);
            var check = new Checks(f, calls);
            var prepare = commands.command("PREPARE");
            check.preparation(prepare);
            calls.call(prepare, f.approver);
            var activate = commands.command("ACTIVATE");
            check.activation(activate);
            check.issuance(app, activate, profile);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Target qualification, pda:assign at PREPARE/ACTIVATE, signed next sequence and original-key replay verified in isolated MySQL.");
    }

    private static final class Checks {
        private final HandoverRollbackFixture f;
        private final HandoverRollbackCommands calls;
        private final JdbcTemplate jdbc;

        Checks(HandoverRollbackFixture f, HandoverRollbackCommands calls) {
            this.f = f;
            this.calls = calls;
            jdbc = f.jdbc;
        }

        void preparation(ObjectNode command) {
            revokedRoles(command);
            revokedAssign(command);
            jdbc.update("DELETE FROM user_warehouse WHERE user_id=? AND warehouse_id=?",
                Ids.bytes(f.operator), Ids.bytes(f.warehouse));
            refused(command, "HANDOVER_TARGET_USER_INVALID");
            jdbc.update("INSERT INTO user_warehouse VALUES(?,?)", Ids.bytes(f.operator), Ids.bytes(f.warehouse));
            revokedDevice(command);
        }

        void activation(ObjectNode command) {
            revokedRoles(command);
            revokedAssign(command);
            revokedDevice(command);
        }

        void issuance(ConfigurableApplicationContext app, ObjectNode command, E2aCrossSourceProfile profile) throws Exception {
            var store = app.getBean(HandoverStore.class);
            long high = SubmissionJson.number(store.caseFacts(f.caseId), "inputHighWatermark");
            assertThat(high).isPositive();
            byte[] original = calls.call(command, f.approver);
            var decision = SubmissionJson.parse(original).path("businessDecision");
            String start = Long.toString(high + 1);
            assertThat(decision.path("startOrderScanSeq").asText()).isEqualTo(start);
            var assignment = app.getBean(AssignmentStore.class)
                .get(UUID.fromString(decision.path("assignmentId").asText()));
            assertThat(assignment.startOrderScanSeq()).isEqualTo(start);
            assertSignedStart(assignment.offlineToken(), app.getBean(OfflineSigner.class), start);
            var snapshot = new HandoverRollbackFacts(f, jdbc).snapshot();
            assertThat(calls.call(command, f.approver)).isEqualTo(original);
            assertThat(new HandoverRollbackFacts(f, jdbc).snapshot()).isEqualTo(snapshot);
            Files.writeString(profile.attempt.resolve("target-qualification.txt"),
                "PREPARE and ACTIVATE each reject revoked pda:assign without effects; high=" + high + ", start=" + start
                    + "; signed claim, row and replay match.");
        }

        private void refused(ObjectNode command, String code) {
            var facts = new HandoverRollbackFacts(f, jdbc);
            var before = facts.snapshot();
            assertThatThrownBy(() -> calls.call(command, f.approver))
                .isInstanceOfSatisfying(BusinessException.class, failure -> assertThat(failure.code()).isEqualTo(code));
            assertThat(facts.snapshot()).isEqualTo(before);
        }

        private void revokedRoles(ObjectNode command) {
            jdbc.update("DELETE FROM user_role WHERE user_id=?", Ids.bytes(f.operator));
            try {
                refused(command, "HANDOVER_TARGET_USER_INVALID");
            } finally {
                jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code IN ('WAREHOUSE_ADMIN','SUPERVISOR')",
                    Ids.bytes(f.operator));
            }
        }

        private void revokedAssign(ObjectNode command) {
            byte[] role = jdbc.queryForObject("SELECT id FROM role WHERE code=?", byte[].class, "WAREHOUSE_ADMIN");
            byte[] permission = jdbc.queryForObject("SELECT id FROM permission WHERE code=?", byte[].class, "pda:assign");
            assertThat(jdbc.update("DELETE FROM role_permission WHERE role_id=? AND permission_id=?", role, permission)).isEqualTo(1);
            try {
                refused(command, "HANDOVER_TARGET_USER_INVALID");
            } finally {
                jdbc.update("INSERT INTO role_permission(role_id,permission_id) VALUES(?,?)", role, permission);
            }
        }

        private void revokedDevice(ObjectNode command) {
            setDevice("DISABLED");
            try {
                refused(command, "HANDOVER_TARGET_DEVICE_INVALID");
            } finally {
                setDevice("ACTIVE");
            }
        }

        private void setDevice(String status) {
            jdbc.update("UPDATE device SET status=?,row_version=row_version+1 WHERE id=?", status, Ids.bytes(f.device));
        }
    }

    private static void assertSignedStart(String token, OfflineSigner signer, String start) throws Exception {
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);
        var key = signer.publicKey();
        var publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key.publicKey())));
        var signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(signature.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
        var claims = SubmissionJson.parse(Base64.getUrlDecoder().decode(parts[1]));
        assertThat(claims.path("startOrderScanSeq").asText()).isEqualTo(start);
    }
}
