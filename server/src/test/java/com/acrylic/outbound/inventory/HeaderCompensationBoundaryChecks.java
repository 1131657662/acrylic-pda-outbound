package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.inventory.HeaderCompensationHttpChecks.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Same-key concurrency, actor ownership, live permission and warehouse boundaries over real HTTP. */
public final class HeaderCompensationBoundaryChecks {
    private HeaderCompensationBoundaryChecks() { }

    public static void verify(WebApplicationContext context, Path attempt) throws Exception {
        concurrent(context, attempt); authorization(context, attempt);
    }

    private static void concurrent(WebApplicationContext context, Path attempt) throws Exception {
        var f = new CorrectionAccountingHttpFixture(context, attempt, true, "header-concurrent");
        String document = approve(f, SubmissionJson.object().put("vehicle", "P06-concurrent"));
        var checker = new SupervisorCorrectionHttp(approver(f, document));
        var maker = new SupervisorCorrectionHttp(f.makerIdentity.user().username());
        var original = f.original(); var before = preserved(f);
        var pending = parallel(f.command(document, "WITHDRAW"), f.checker, checker);
        var confirm = f.command(document, "WITHDRAW_CONFIRM");
        byte[] completed = parallel(confirm, f.maker, maker);
        SupervisorCorrectionReceipt.verify(completed, f.publicKey);
        var different = confirm.deepCopy().put("reason", "different original-key bytes");
        var mismatch = f.maker.command(different, 409);
        assertThat(SubmissionJson.parse(mismatch).path("code").asText()).isEqualTo("IDEMPOTENCY_CONTENT_MISMATCH");
        assertThat(f.result().path("version").asLong()).isEqualTo(3);
        assertThat(preserved(f)).isEqualTo(before); assertThat(f.original()).isEqualTo(original);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM correction_command_receipt WHERE document_id=UUID_TO_BIN(?)",
            Integer.class, document)).isEqualTo(4);
        Files.write(f.evidence.resolve("concurrent-pending.json"), pending);
        Files.write(f.evidence.resolve("concurrent-confirmed.json"), completed);
        Files.write(f.evidence.resolve("different-body-denied.json"), mismatch);
    }

    private static byte[] parallel(ObjectNode command, SupervisorCorrectionHttp first, SupervisorCorrectionHttp second) throws Exception {
        var left = submit(first, command); var right = submit(second, command);
        byte[] original = left.get(45, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(right.get(45, java.util.concurrent.TimeUnit.SECONDS)).containsExactly(original);
        return original;
    }

    private static CompletableFuture<byte[]> submit(SupervisorCorrectionHttp actor, ObjectNode command) {
        return CompletableFuture.supplyAsync(() -> {
            try { return actor.command(command, 200); }
            catch (Exception failure) { throw new CompletionException(failure); }
        });
    }

    private static void authorization(WebApplicationContext context, Path attempt) throws Exception {
        var f = new CorrectionAccountingHttpFixture(context, attempt, false, "header-permissions");
        String document = approve(f, SubmissionJson.object().put("vehicle", "P06-permission"));
        var outsider = IdentityFixture.create(f.jdbc, context.getBean(PasswordEncoder.class), "SUPERVISOR");
        var cross = new SupervisorCorrectionHttp(outsider.user().username());
        var command = f.command(document, "WITHDRAW");
        var wrongWarehouse = command.deepCopy().put("warehouseId", outsider.warehouse().toString());
        byte[] denial = cross.command(wrongWarehouse, 404);
        assertThat(SubmissionJson.parse(denial).path("code").asText()).isEqualTo("CORRECTION_DOCUMENT_NOT_FOUND");
        Files.write(f.evidence.resolve("cross-warehouse-denied.json"), denial);
        f.jdbc.update("INSERT INTO user_warehouse VALUES(?,UUID_TO_BIN(?))", Ids.bytes(outsider.user().id()), f.warehouse);
        cross = new SupervisorCorrectionHttp(outsider.user().username());
        var before = preserved(f);
        Files.write(f.evidence.resolve("wrong-approver-denied.json"), cross.command(command, 403));
        revoked(f, command);
        byte[] pending = f.checker.command(command, 200);
        Files.write(f.evidence.resolve("pending.json"), pending);
        var confirm = f.command(document, "WITHDRAW_CONFIRM");
        Files.write(f.evidence.resolve("wrong-confirmer-denied.json"), cross.command(confirm, 403));
        byte[] completed = f.maker.command(confirm, 200);
        SupervisorCorrectionReceipt.verify(completed, f.publicKey);
        Files.write(f.evidence.resolve("confirmed.json"), completed);
        Files.write(f.evidence.resolve("leaked-key-denied.json"), cross.command(command, 404));
        assertThat(preserved(f)).isEqualTo(before);
    }

    private static void revoked(CorrectionAccountingHttpFixture f, ObjectNode command) throws Exception {
        String username = approver(f, command.path("documentId").asText());
        String actor = f.jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM app_user WHERE username=?", String.class, username);
        var roles = f.jdbc.queryForList("SELECT BIN_TO_UUID(role_id) FROM user_role WHERE user_id=UUID_TO_BIN(?)", String.class, actor);
        f.jdbc.update("DELETE FROM user_role WHERE user_id=UUID_TO_BIN(?)", actor);
        try { Files.write(f.evidence.resolve("revoked-approver-denied.json"), f.checker.command(command, 403)); }
        finally { for (String role : roles) f.jdbc.update("INSERT INTO user_role VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?))", actor, role); }
        assertThat(f.result().path("version").asLong()).isEqualTo(2);
        assertThat(f.jdbc.queryForObject("SELECT status FROM correction_document WHERE id=UUID_TO_BIN(?)", String.class,
            command.path("documentId").asText())).isEqualTo("EFFECTIVE");
    }

    private static String approver(CorrectionAccountingHttpFixture f, String document) {
        return f.jdbc.queryForObject("SELECT u.username FROM correction_document d JOIN app_user u ON u.id=d.approved_by"
            + " WHERE d.id=UUID_TO_BIN(?)", String.class, document);
    }
}
