package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class TemporaryContinuationLifecycleStoreTest {
    @Test void completionRejectsNonCanonicalOrEmptyClosureProofBeforeWriting() {
        var proof = new TemporaryContinuationLifecycleStore.Completion(UUID.randomUUID(), UUID.randomUUID(), 1,
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "{\"x\":1,\"status\":\"SUCCEEDED\"}", Instant.now());

        assertThatThrownBy(() -> new TemporaryContinuationLifecycleStore(mock(JdbcTemplate.class)).complete(proof))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> org.assertj.core.api.Assertions.assertThat(error.code())
                    .isEqualTo("CONTINUATION_CLOSURE_PROOF_INVALID"));
    }

    @Test void cancellationRemainsRejectedUntilAReleasePolicyAndProofExist() {
        assertThatThrownBy(() -> new TemporaryContinuationLifecycleStore(mock(JdbcTemplate.class)).cancel(UUID.randomUUID()))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> org.assertj.core.api.Assertions.assertThat(error.code())
                    .isEqualTo("CONTINUATION_CANCEL_POLICY_UNDEFINED"));
    }
}
