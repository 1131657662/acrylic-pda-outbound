package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.TemporaryProtocol;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CommandProtocolCompatibilityTest {
    @Test void originalCommandWithoutDispositionFieldPreservesItsCanonicalBytes() {
        var original = CommandReceiptFixture.write().command().bytes();
        assertThat(SubmissionJson.parse(original).has("dispositionManifest")).isFalse();
        assertThat(TemporaryProtocol.command(original)).isEqualTo(original);
    }

    @Test void explicitNullIsAcceptedWithoutRewritingTheSavedCommand() {
        var input = CommandReceiptFixture.write().command();
        var body = (ObjectNode) input.body();
        body.putNull("dispositionManifest");
        byte[] bytes = TemporaryProtocol.canonical(body.toString().getBytes(StandardCharsets.UTF_8));
        assertThat(TemporaryProtocol.command(bytes)).isEqualTo(bytes);
        assertThat(TemporaryProtocol.commandSha256(bytes)).isNotEqualTo(input.digest());
    }

    @Test void normalConfirmationRejectsAnInapplicableDispositionManifest() {
        var body = (ObjectNode) CommandReceiptFixture.write().command().body();
        body.putObject("dispositionManifest").put("version", 1);
        assertThatThrownBy(() -> TemporaryProtocol.command(body.toString().getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("DISPOSITION_MANIFEST_NOT_APPLICABLE");
    }
}
