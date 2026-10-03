package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.devices.DeviceStore;
import com.acrylic.outbound.pda.PdaSessions;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Earlier authentic recall stop is reused only after signature verification; route-bound keys cannot cross cases. */
public final class HandoverStopExistingChecks {
    private final HandoverStopFixture first, second;
    private final HandoverStopFacts facts;
    private final WebApplicationContext app;
    private byte[] firstReceipt;

    public HandoverStopExistingChecks(WebApplicationContext app, Path attempt) throws Exception {
        this.app = app; first = new HandoverStopFixture(app, attempt.resolve("stop-existing"));
        second = new HandoverStopFixture(app, attempt.resolve("stop-cross-case"), first);
        facts = new HandoverStopFacts(first, first.jdbc);
    }

    public void verify() throws Exception {
        wrongOperator();
        var authentic = (ObjectNode) first.r.stop(); var damaged = authentic.deepCopy().put("signedReceipt", "bad.signature.parts");
        storeRecall(damaged);
        try {
            var before = facts.snapshot(); first.post(first.command, first.r.f.tokens, 409, "HANDOVER_RECEIPT_INTEGRITY_FAILURE"); facts.unchanged(before);
        } finally { storeRecall(authentic); }
        first.jdbc.update("UPDATE offline_authorization SET expires_at=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(3)) WHERE assignment_id=UUID_TO_BIN(?)", first.r.f.assignment.path("id").asText());
        firstReceipt = first.post(first.command, first.r.f.tokens, 200, null); facts.stopped();
        assertThat(SubmissionJson.parse(firstReceipt).path("recallStop")).isEqualTo(authentic);
        var before = facts.snapshot();
        second.post(second.command.deepCopy().put("commandId", first.command.path("commandId").asText()), second.r.f.tokens, 409, "IDEMPOTENCY_CONTENT_MISMATCH");
        facts.unchanged(before);
    }

    private void wrongOperator() throws Exception {
        var identity = IdentityFixture.create(first.jdbc, app.getBean(PasswordEncoder.class), "WAREHOUSE_ADMIN");
        first.jdbc.update("INSERT INTO user_warehouse VALUES(?,?)", Ids.bytes(identity.user().id()), Ids.bytes(first.r.f.web.identity().warehouse()));
        var device = app.getBean(DeviceStore.class).find(UUID.fromString(first.r.f.tokens.path("deviceId").asText()), false);
        // Deliberate negative-only session fixture: the positive flow uses real EC challenge login.
        var token = app.getBean(PdaSessions.class).issue(identity.user(), device);
        var before = facts.snapshot();
        first.post(first.command, SubmissionJson.object().put("accessToken", token.accessToken()), 404, "ASSIGNMENT_NOT_FOUND");
        facts.unchanged(before);
    }

    private void storeRecall(ObjectNode value) {
        first.jdbc.update("UPDATE order_change_request SET stop_receipt=? WHERE id=UUID_TO_BIN(?)",
            SubmissionJson.canonical(value), first.r.recall.path("changeRequestId").asText());
    }

    public void restart(WebApplicationContext context) throws Exception {
        var current = new HandoverStopFacts(first, context.getBean(JdbcTemplate.class)); var before = current.snapshot();
        assertThat(first.post(first.command, first.r.f.tokens, 200, null)).isEqualTo(firstReceipt);
        current.unchanged(before);
    }
}