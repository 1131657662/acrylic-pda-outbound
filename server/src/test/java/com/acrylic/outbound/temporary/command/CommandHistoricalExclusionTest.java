package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.TemporaryProtocol;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CommandHistoricalExclusionTest {
    @Test void readsLegacySyntheticOriginalWithoutAllowingNewUnsignedExclusion() {
        var command=CommandReceiptFixture.write().command().body().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode)command).put("commandType","DUPLICATE_EXCLUDE").put("reason","historical synthetic exclusion");
        var bytes=SubmissionJson.canonical(command).getBytes(StandardCharsets.UTF_8);
        var stored=CommandInput.stored(bytes);
        assertThat(stored.bytes()).containsExactly(bytes);
        assertThat(stored.digest()).isEqualTo(TemporaryProtocol.storedCommandSha256(bytes));
        assertThatThrownBy(()->CommandInput.parse(bytes)).isInstanceOf(com.acrylic.outbound.common.BusinessException.class);
    }
}
