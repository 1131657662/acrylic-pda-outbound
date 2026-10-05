package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TemporaryLinkOperationTest {
    @Test
    void rejectsUnmappedSourceBeforeReadingLinkDependencies() {
        var fixture=CommandReceiptFixture.write();
        var facts=(ObjectNode)fixture.context().json();facts.put("mappingVersion","0");
        var context=new CommandContext(facts);
        var source=mock(TemporaryConfirmationSource.class);
        var candidates=mock(com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates.class);
        var targets=mock(com.acrylic.outbound.temporary.review.TemporaryLinkTarget.class);
        var mappings=mock(TemporaryLinkMapping.class);
        var operation=new TemporaryLinkOperation(new TemporaryLinkOperation.Dependencies(source,candidates,targets,mappings));
        var write=new CommandWrite(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),Instant.now(),fixture.command(),context,fixture.actor());

        assertThatThrownBy(() -> operation.execute(write))
            .isInstanceOfSatisfying(BusinessException.class, error -> {
                org.assertj.core.api.Assertions.assertThat(error.code()).isEqualTo("COMMAND_CONTEXT_CHANGED");
                org.assertj.core.api.Assertions.assertThat(error.status()).isEqualTo(409);
            });
        verifyNoInteractions(source,candidates,targets,mappings);
    }
}
