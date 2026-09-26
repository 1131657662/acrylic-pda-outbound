package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class UnsubmittedSourceFallbackTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final UnsubmittedSourceFacts fallback = mock(UnsubmittedSourceFacts.class);
    private final SourceEvidenceIntakeOtherFacts facts = new SourceEvidenceIntakeOtherFacts(jdbc, ignored -> null, fallback);
    private final SourceEvidenceIntakePageObjects.Binding binding = new SourceEvidenceIntakePageObjects.Binding(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    private final E2aPageItem capture = new E2aPageItem("CAPTURE", UUID.randomUUID(), "CAPTURE_RECORDS", UUID.randomUUID(),
        "a".repeat(64), 1, 4, "a".repeat(64), "WAITING_CONFIRM", null, null, null);

    @Test void existingOwnerCannotBeReclassifiedAsUnsubmitted() {
        when(jdbc.queryForList(contains("WHERE id=? OR"), eq(String.class), any(Object[].class)))
            .thenReturn(List.of(UUID.randomUUID().toString()));
        assertThatThrownBy(() -> facts.require(binding, List.of(capture), 12))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("E2A_OTHER_CAPTURE_NOT_STORED"));
        verifyNoInteractions(fallback);
    }

    @Test void unownedCaptureRequiresTrustedFallbackBeforeItCanComplete() {
        when(fallback.validate(binding, List.of(capture), 12))
            .thenThrow(new BusinessException(409, "E2A_OTHER_UNSUBMITTED_UNVERIFIABLE", "untrusted"));
        assertThatThrownBy(() -> facts.require(binding, List.of(capture), 12))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("E2A_OTHER_UNSUBMITTED_UNVERIFIABLE"));
    }
}
