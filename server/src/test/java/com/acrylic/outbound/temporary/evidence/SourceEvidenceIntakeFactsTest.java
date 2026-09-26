package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceEvidenceIntakeFactsTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final SourceEvidenceIntakeFacts facts=new SourceEvidenceIntakeFacts(jdbc);

    @Test void existingSourceRelationshipIsReadWithoutOriginalPayloadOrMapping() {
        var submission=UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        when(jdbc.queryForList(anyString(),any(Class.class),any(Object[].class)))
            .thenReturn(List.of("{\"submissionId\":\""+submission+"\",\"workRound\":\"2\"}"));

        var context=facts.read(submission);

        assertThat(context.id("submissionId")).isEqualTo(submission);
        var sql=ArgumentCaptor.forClass(String.class);var args=ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForList(sql.capture(),any(Class.class),args.capture());
        assertThat(sql.getValue()).contains("JOIN temporary_work_origin", "JOIN temporary_authorization")
            .contains("a.recovery_epoch=s.source_recovery_epoch", "c.source_order_id IS NULL")
            .doesNotContain("submission_payload", "submission_upload", "temporary_source_mapping", "FOR UPDATE");
        assertThat(args.getValue()).hasSize(1);
        assertThat((byte[])args.getValue()[0]).containsExactly(Ids.bytes(submission));
    }

    @Test void missingSourceFailsClosedBeforeIntakeLookup() {
        when(jdbc.queryForList(anyString(),any(Class.class),any(Object[].class))).thenReturn(List.of());

        assertThatThrownBy(() -> facts.read(UUID.randomUUID()))
            .isInstanceOfSatisfying(BusinessException.class,error->assertThat(error.code()).isEqualTo("E2A_SOURCE_NOT_FOUND"));
    }
}
