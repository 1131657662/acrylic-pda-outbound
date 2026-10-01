package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Real identities exercise ownership and exact-once effects of a two-phase withdrawal. */
public final class SupervisorWithdrawalChecks {
    private final SupervisorCorrectionFixture fixture;
    private final SupervisorWithdrawalFacts facts;
    private final SupervisorCorrectionHttp maker, checker, otherMaker, otherChecker;
    private final ObjectNode denials = SubmissionJson.object();

    public SupervisorWithdrawalChecks(WebApplicationContext context) throws Exception {
        fixture = new SupervisorCorrectionFixture(context); facts = new SupervisorWithdrawalFacts(fixture);
        maker = new SupervisorCorrectionHttp(fixture.source.operator().username());
        checker = new SupervisorCorrectionHttp(fixture.checker.user().username());
        otherMaker = outsider(context, "WAREHOUSE_ADMIN"); otherChecker = outsider(context, "SUPERVISOR");
    }

    public void verify(Path attempt) throws Exception {
        var before = fixture.immutableSource();
        byte[] created = maker.command(fixture.create(), 200);
        var receipt = SupervisorCorrectionReceipt.verify(created, attempt.resolve("private/server-public.der"));
        String document = receipt.at("/businessDecision/documentId").asText(); assertThat(document).isNotBlank();
        byte[] approved = checker.command(fixture.approve(document), 200);
        SupervisorCorrectionReceipt.verify(approved, attempt.resolve("private/server-public.der"));
        var originalLedger = facts.ledger(); var effective = fixture.result();
        var withdraw = command(document, "WITHDRAW"); rejectInitiation(document, withdraw);
        byte[] pending = checker.command(withdraw, 200);
        var pendingReceipt = SupervisorCorrectionReceipt.verify(pending, attempt.resolve("private/server-public.der"));
        assertThat(pendingReceipt.at("/businessDecision/status").asText()).isEqualTo("WITHDRAW_PENDING");
        assertThat(pendingReceipt.at("/businessDecision/rolledBack").asBoolean()).isFalse();
        assertThat(facts.ledger()).isEqualTo(originalLedger); assertThat(fixture.result()).isEqualTo(effective);
        var pendingState = facts.state(document); var confirm = command(document, "WITHDRAW_CONFIRM");
        rejectConfirmation(document, confirm); byte[] confirmed = maker.command(confirm, 200);
        var finalReceipt = SupervisorCorrectionReceipt.verify(confirmed, attempt.resolve("private/server-public.der"));
        assertThat(finalReceipt.at("/businessDecision/status").asText()).isEqualTo("WITHDRAWN");
        replay(document, new ObjectNode[]{withdraw, confirm}, new byte[][]{pending, confirmed});
        facts.assertReversal(originalLedger, document); assertThat(fixture.immutableSource()).isEqualTo(before);
        var proof = SubmissionJson.object().put("orderId", fixture.order).put("documentId", document);
        proof.set("pending", pendingState); proof.set("final", facts.state(document)); proof.set("denials", denials);
        proof.set("sourceBefore", before); proof.set("sourceAfter", fixture.immutableSource());
        Files.writeString(attempt.resolve("withdrawal-evidence.json"), SubmissionJson.canonical(proof));
        saveReceipts(attempt, new byte[][]{created, approved, pending, confirmed});
        Files.writeString(attempt.resolve("real-records.tsv"), fixture.order + "\t" + document + System.lineSeparator());
    }

    private SupervisorCorrectionHttp outsider(WebApplicationContext context, String role) throws Exception {
        var identity = IdentityFixture.create(fixture.jdbc, context.getBean(PasswordEncoder.class), role);
        fixture.jdbc.update("INSERT INTO user_warehouse VALUES(?,UUID_TO_BIN(?))", Ids.bytes(identity.user().id()), fixture.warehouse);
        String permission = role.equals("SUPERVISOR") ? "correction:approve" : "outbound:correct";
        assertThat(fixture.permissions(identity.user().id())).contains(permission);
        return new SupervisorCorrectionHttp(identity.user().username());
    }

    private ObjectNode command(String document, String kind) { return fixture.approve(document).put("commandType", kind); }

    private void rejectInitiation(String document, ObjectNode command) throws Exception {
        var state = facts.state(document);
        denied("makerCannotInitiate", maker, command);
        denied("differentSupervisorCannotInitiate", otherChecker, command);
        assertThat(facts.state(document)).isEqualTo(state);
    }

    private void rejectConfirmation(String document, ObjectNode command) throws Exception {
        var state = facts.state(document);
        denied("supervisorCannotConfirm", checker, command);
        denied("differentMakerCannotConfirm", otherMaker, command);
        assertThat(facts.state(document)).isEqualTo(state);
    }

    private void denied(String label, SupervisorCorrectionHttp actor, ObjectNode command) throws Exception {
        var response = SubmissionJson.parse(actor.command(command, 403));
        assertThat(response.path("code").asText()).isEqualTo("CORRECTION_PERMISSION_DENIED");
        denials.set(label, response);
    }

    private void replay(String document, ObjectNode[] commands, byte[][] receipts) throws Exception {
        var before = facts.state(document);
        assertThat(checker.command(commands[0], 200)).isEqualTo(receipts[0]);
        assertThat(maker.command(commands[1], 200)).isEqualTo(receipts[1]);
        assertThat(SubmissionJson.parse(checker.command(commands[0].deepCopy().put("reason", "different bytes"), 409))
            .path("code").asText()).isEqualTo("IDEMPOTENCY_CONTENT_MISMATCH");
        assertThat(SubmissionJson.parse(maker.command(commands[1].deepCopy().put("reason", "different bytes"), 409))
            .path("code").asText()).isEqualTo("IDEMPOTENCY_CONTENT_MISMATCH");
        assertThat(facts.state(document)).isEqualTo(before);
    }

    private void saveReceipts(Path attempt, byte[][] bytes) throws Exception {
        String[] names = {"create-response.json", "approve-response.json", "withdraw-response.json", "confirm-response.json"};
        for (int i = 0; i < names.length; i++) Files.write(attempt.resolve(names[i]), bytes[i]);
    }
}