package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.command.CommandContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SourceEvidencePendingHandoverTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final UUID submission=UUID.randomUUID();

    @Test void actualCurrentSourceKeepsItsExistingEvidenceRoute() {
        var context=context(submission,UUID.randomUUID());
        assertThat(SourceEvidencePendingHandover.matches(jdbc,context,submission)).isTrue();
        verifyNoInteractions(jdbc);
    }
    @Test void sourceWithoutCanonicalCannotUseThePendingHandoverRoute() {
        assertThat(SourceEvidencePendingHandover.matches(jdbc,context(null,null),submission)).isFalse();
        verifyNoInteractions(jdbc);
    }
    @Test void anotherCurrentSubmissionCannotBeOverriddenByPendingEvidence() {
        assertThat(SourceEvidencePendingHandover.matches(jdbc,context(UUID.randomUUID(),UUID.randomUUID()),submission)).isFalse();
        verifyNoInteractions(jdbc);
    }
    @Test void pathSubmissionMustMatchTheTrustedSourceContext() {
        assertThat(SourceEvidencePendingHandover.matches(jdbc,context(submission,UUID.randomUUID()),UUID.randomUUID())).isFalse();
        verifyNoInteractions(jdbc);
    }
    private CommandContext context(UUID current,UUID canonical) {
        return new CommandContext(SubmissionJson.object().put("submissionId",submission.toString())
            .put("currentSubmissionId",current==null?null:current.toString())
            .put("canonicalOrderId",canonical==null?null:canonical.toString()));
    }
}
