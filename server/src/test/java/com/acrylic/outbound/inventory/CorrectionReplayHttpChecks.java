package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Same-warehouse principals probe the actual POST replay path after the first command commits. */
public final class CorrectionReplayHttpChecks {
    private final SupervisorCorrectionFixture fixture;
    private final SupervisorWithdrawalFacts facts;
    private final SupervisorCorrectionHttp maker, checker, otherMaker, otherChecker;
    private final ObjectNode proof = SubmissionJson.object();

    public CorrectionReplayHttpChecks(WebApplicationContext context) throws Exception {
        fixture = new SupervisorCorrectionFixture(context); facts = new SupervisorWithdrawalFacts(fixture);
        maker = new SupervisorCorrectionHttp(fixture.source.operator().username());
        checker = new SupervisorCorrectionHttp(fixture.checker.user().username());
        otherMaker = outsider(context, "WAREHOUSE_ADMIN"); otherChecker = outsider(context, "SUPERVISOR");
    }

    public void verify(Path attempt) throws Exception {
        var create = fixture.create(); byte[] created = maker.command(create, 200);
        String document = SupervisorCorrectionReceipt.verify(created, attempt.resolve("private/server-public.der"))
            .at("/businessDecision/documentId").asText();
        verifyReplay(create, new ReplayActors(maker, otherMaker), created);
        var approve = fixture.approve(document); byte[] approved = checker.command(approve, 200);
        verifyReplay(approve, new ReplayActors(checker, otherChecker), approved);
        var withdraw = fixture.approve(document).put("commandType", "WITHDRAW");
        byte[] pending = checker.command(withdraw, 200);
        verifyReplay(withdraw, new ReplayActors(checker, otherChecker), pending);
        var confirm = fixture.approve(document).put("commandType", "WITHDRAW_CONFIRM");
        byte[] confirmed = maker.command(confirm, 200);
        verifyReplay(confirm, new ReplayActors(maker, otherMaker), confirmed);
        assertThat(SubmissionJson.parse(confirmed).at("/businessDecision/status").asText()).isEqualTo("WITHDRAWN");
        proof.set("final", facts.state(document));
        Files.writeString(attempt.resolve("replay-ownership-evidence.json"), proof.toPrettyString());
    }

    private void verifyReplay(ObjectNode command, ReplayActors actors, byte[] original) throws Exception {
        String document = SubmissionJson.parse(original).path("documentId").asText();
        var before = facts.state(document);
        assertThat(actors.owner().command(command, 200)).isEqualTo(original);
        var denied = SubmissionJson.parse(actors.other().command(command, 404));
        assertThat(denied.path("code").asText()).isEqualTo("CORRECTION_DOCUMENT_NOT_FOUND");
        var changed = command.deepCopy().put("reason", "different content");
        assertThat(SubmissionJson.parse(actors.other().command(changed, 404)).path("code").asText())
            .isEqualTo("CORRECTION_DOCUMENT_NOT_FOUND");
        assertThat(SubmissionJson.parse(actors.owner().command(changed, 409)).path("code").asText())
            .isEqualTo("IDEMPOTENCY_CONTENT_MISMATCH");
        assertThat(facts.state(document)).isEqualTo(before);
        var entry = proof.putObject(command.path("commandType").asText());
        entry.put("originalSha256", SubmissionJson.sha(SubmissionJson.parse(original)));
        entry.set("denial", denied); entry.set("before", before); entry.set("after", facts.state(document));
    }

    private SupervisorCorrectionHttp outsider(WebApplicationContext context, String role) throws Exception {
        var identity = IdentityFixture.create(fixture.jdbc, context.getBean(PasswordEncoder.class), role);
        fixture.jdbc.update("INSERT INTO user_warehouse VALUES(?,UUID_TO_BIN(?))", Ids.bytes(identity.user().id()), fixture.warehouse);
        String permission = role.equals("SUPERVISOR") ? "correction:approve" : "outbound:correct";
        assertThat(fixture.permissions(identity.user().id())).contains(permission);
        return new SupervisorCorrectionHttp(identity.user().username());
    }

    private record ReplayActors(SupervisorCorrectionHttp owner, SupervisorCorrectionHttp other) { }
}
