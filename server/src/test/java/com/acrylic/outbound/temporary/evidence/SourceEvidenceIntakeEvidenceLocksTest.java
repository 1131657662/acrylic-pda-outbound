package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.command.CommandContext;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class SourceEvidenceIntakeEvidenceLocksTest {
    @Test void locksSourceEvidenceInFixedTableOrder() {
        var jdbc=mock(JdbcTemplate.class);
        var locks=new SourceEvidenceIntakeEvidenceLocks(jdbc);
        var context=new CommandContext(SubmissionJson.parse("""
            {"scopeId":"11111111-1111-4111-8111-111111111111",
             "submissionId":"22222222-2222-4222-8222-222222222222","workRound":"3"}
            """));

        locks.source(context);

        InOrder order=inOrder(jdbc);
        for(String table:new String[]{"source_evidence_set","source_evidence_object","source_evidence_item",
                "source_evidence_verification","source_evidence_intake_receipt","source_evidence_page_item_stage"}) {
            order.verify(jdbc).query(argThat(sql->sql.contains("FROM "+table) && sql.endsWith("FOR UPDATE")
                && sql.contains("ORDER BY ")),any(RowCallbackHandler.class),any(Object[].class));
        }
        order.verifyNoMoreInteractions();
    }

    @Test void requiresCallerWriteTransaction() throws Exception {
        var method=SourceEvidenceIntakeEvidenceLocks.class.getMethod("source",CommandContext.class);
        var transaction=new AnnotationTransactionAttributeSource().getTransactionAttribute(method,SourceEvidenceIntakeEvidenceLocks.class);

        assertThat(transaction.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_MANDATORY);
    }
}
