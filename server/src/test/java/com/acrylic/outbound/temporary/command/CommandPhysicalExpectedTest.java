package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CommandPhysicalExpectedTest {
    @Test void dispositionManifestDoesNotHaveToEqualTheEvidenceSetHash() {
        var fixture = fixture();
        assertThatCode(() -> CommandExpected.validate(fixture.input(), fixture.context(), fixture.actor()))
            .doesNotThrowAnyException();
    }

    @Test void changedEvidenceSetIsStillRejected() {
        var fixture = fixture();
        var context = (ObjectNode) fixture.context().json();
        context.put("evidenceSetSha256", "c".repeat(64));
        assertThatThrownBy(() -> CommandExpected.validate(fixture.input(), new CommandContext(context), fixture.actor()))
            .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("COMMAND_CONTEXT_CHANGED");
    }

    private Fixture fixture() {
        var write = CommandReceiptFixture.write();
        var context = (ObjectNode) write.context().json();
        var body = (ObjectNode) write.command().body();
        var expected = (ObjectNode) body.get("expected");
        for (String field : List.of("evidenceSetId", "verificationId")) {
            String value = UUID.randomUUID().toString(); context.put(field, value); expected.put(field, value);
        }
        for (String field : List.of("verificationVersion", "verificationAlgorithmVersion")) {
            context.put(field, "1"); expected.put(field, "1");
        }
        context.put("evidenceSetSha256", "a".repeat(64)); expected.put("evidenceSetSha256", "a".repeat(64));
        expected.put("dispositionManifestSha256", "b".repeat(64)).put("dispositionManifestVersion", "1");
        body.put("commandType", "PHYSICAL_DISPOSITION").put("reason", "Unloaded bale verification");
        body.putObject("dispositionManifest").put("version", 1).putArray("items");
        var actor = new CommandIdentity.Actor(UUID.randomUUID(), UUID.fromString(context.path("warehouseId").asText()),
            "1", null, List.of("order:dispose"), List.of(context.path("warehouseId").asText()));
        return new Fixture(CommandInput.parse(body.toString().getBytes(StandardCharsets.UTF_8)), new CommandContext(context), actor);
    }

    private record Fixture(CommandInput input, CommandContext context, CommandIdentity.Actor actor) { }
}
