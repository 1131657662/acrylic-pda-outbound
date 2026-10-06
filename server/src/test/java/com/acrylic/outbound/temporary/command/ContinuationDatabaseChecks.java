package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Composed database checks, with real signature and replay code and explicitly synthetic identities. */
public final class ContinuationDatabaseChecks {
    private ContinuationDatabaseChecks() { }

    public static void verify(JdbcTemplate jdbc, Map<String, String> seed, Path directory) throws Exception {
        var fixture = new ContinuationDatabaseCommand(jdbc, seed, directory);
        var receipt = fixture.execute(); var unsigned = SubmissionJson.parse(receipt.unsigned());
        var record = fixture.store.find(fixture.actor.id(), fixture.input().key());
        assertThat(fixture.integrity.verify(record)).isEqualTo(unsigned);
        var replay = new CommandReplay(fixture.store, fixture.integrity);
        assertThat(replay.existing(fixture.actor, fixture.input())).isEqualTo(receipt.original());
        assertThat(unsigned.path("commandType").asText()).isEqualTo("REOPEN_WORK");
        assertThat(unsigned.path("businessDecision").path("products")).hasSize(3);
        assertThat(unsigned.path("capabilities")).isEqualTo(CommandReceiptCodec.capabilities());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM temporary_source_continuation_bale", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT work_round FROM submission_scope WHERE id=?", Long.class,
            Ids.bytes(fixture.context.id("scopeId")))).isEqualTo(1);
        rejectsChangedInput(fixture, replay);
        rejectsReceiptMismatch(fixture, unsigned);
        rejectsSecondTicket(fixture);
    }

    public static void execute(JdbcTemplate jdbc, Map<String, String> seed, Path directory) throws Exception {
        new ContinuationDatabaseCommand(jdbc, seed, directory).execute();
    }

    private static void rejectsChangedInput(ContinuationDatabaseCommand fixture, CommandReplay replay) {
        var changed = fixture.body.deepCopy().put("reason", "different intent under original key");
        assertThatThrownBy(() -> replay.existing(fixture.actor, CommandInput.parse(CommandReceiptCodec.bytes(changed))))
            .extracting("code").isEqualTo("IDEMPOTENCY_CONTENT_MISMATCH");
        var unauthorized = new CommandIdentity.Actor(fixture.actor.id(), fixture.actor.warehouse(), "1", null,
            List.of("order:dispose"), fixture.actor.warehouses());
        assertThatThrownBy(() -> replay.existing(unauthorized, fixture.input()))
            .extracting("code").isEqualTo("COMMAND_RECEIPT_NOT_FOUND");
        assertThatThrownBy(() -> CommandPermissions.requireWrite(unauthorized.permissions(), "REOPEN_WORK"))
            .extracting("code").isEqualTo("PERMISSION_DENIED");
        var foreign = new CommandIdentity.Actor(UUID.randomUUID(), fixture.actor.warehouse(), "1", null,
            fixture.actor.permissions(), fixture.actor.warehouses());
        assertThat(replay.existing(foreign, fixture.input())).isNull();
        var wrongSource = fixture.body.deepCopy().put("authorizationId", UUID.randomUUID().toString());
        assertThatThrownBy(() -> CommandExpected.validate(CommandInput.parse(CommandReceiptCodec.bytes(wrongSource)),
            fixture.context, fixture.actor)).extracting("code").isEqualTo("COMMAND_CONTEXT_CHANGED");
    }

    private static void rejectsReceiptMismatch(ContinuationDatabaseCommand fixture, com.fasterxml.jackson.databind.JsonNode unsigned) {
        for (String key : List.of("verificationId", "retainedManifestSha256", "fromWorkRound", "nextOrderScanSeq")) {
            var damaged = (ObjectNode) unsigned.deepCopy();
            ((ObjectNode) damaged.path("businessDecision")).put(key, "wrong");
            assertThatThrownBy(() -> new TemporaryContinuationIntegrity(fixture.jdbc).verify(damaged))
                .extracting("code").isEqualTo("COMMAND_RECEIPT_INTEGRITY_FAILURE");
        }
        var missingBale = (ObjectNode) unsigned.deepCopy();
        ((com.fasterxml.jackson.databind.node.ArrayNode) missingBale.path("businessDecision").path("products")).remove(0);
        assertThatThrownBy(() -> new TemporaryContinuationIntegrity(fixture.jdbc).verify(missingBale))
            .extracting("code").isEqualTo("COMMAND_RECEIPT_INTEGRITY_FAILURE");
    }

    private static void rejectsSecondTicket(ContinuationDatabaseCommand fixture) {
        assertThatThrownBy(() -> fixture.operation.execute(fixture.write()))
            .extracting("code").isEqualTo("CONTINUATION_ALREADY_ISSUED");
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_source_continuation", Integer.class)).isEqualTo(1);
    }
}
