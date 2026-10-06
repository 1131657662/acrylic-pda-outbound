package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Real Web identities exercise warehouse, permission and separation-of-duty denials. */
final class TemporaryDispositionAccessChecks {
    private final TemporaryDispositionHttp original;
    private final TemporaryDispositionHttp readOnly;
    private final TemporaryDispositionHttp foreign;

    TemporaryDispositionAccessChecks(WebApplicationContext context, Path attempt, String submission) throws Exception {
        var jdbc = context.getBean(JdbcTemplate.class);
        var passwords = context.getBean(PasswordEncoder.class);
        var source = jdbc.queryForMap("SELECT u.username,BIN_TO_UUID(c.warehouse_id) warehouse FROM submission s "
            + "JOIN submission_scope c ON c.id=s.scope_id JOIN app_user u ON u.id=s.operator_id WHERE s.id=UUID_TO_BIN(?)", submission);
        original = new TemporaryDispositionHttp(attempt.resolve("disposition-original-actor"));
        original.login(source.get("username").toString());
        var reader = IdentityFixture.create(jdbc, passwords, "TEMPORARY_ORDER_CONFIRMER");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,UUID_TO_BIN(?))", Ids.bytes(reader.user().id()), source.get("warehouse"));
        readOnly = new TemporaryDispositionHttp(attempt.resolve("disposition-read-only"));
        readOnly.login(reader.user().username());
        var outsider = IdentityFixture.create(jdbc, passwords, "TEMPORARY_ORDER_CONFIRMER");
        foreign = new TemporaryDispositionHttp(attempt.resolve("disposition-foreign-warehouse"));
        foreign.login(outsider.user().username());
    }

    void rejection(ObjectNode command) throws Exception {
        assertThat(SubmissionJson.parse(original.command(command, 403)).path("code").asText())
            .isEqualTo("TEMPORARY_REJECTION_SELF_FORBIDDEN");
        noPermission(command);
    }

    void noPermission(ObjectNode command) throws Exception {
        assertThat(SubmissionJson.parse(readOnly.command(command, 403)).path("code").asText()).isEqualTo("PERMISSION_DENIED");
        assertThat(SubmissionJson.parse(foreign.command(command, 404)).path("code").asText()).isEqualTo("COMMAND_RECEIPT_NOT_FOUND");
    }

    void differentActorCannotRecover(ObjectNode command) throws Exception {
        String path = "/temporary-command-receipts/" + command.path("commandId").asText() + "/original?warehouseId="
            + command.path("warehouseId").asText();
        assertThat(SubmissionJson.parse(readOnly.get(path, 404)).path("code").asText()).isEqualTo("COMMAND_RECEIPT_NOT_FOUND");
    }
}
