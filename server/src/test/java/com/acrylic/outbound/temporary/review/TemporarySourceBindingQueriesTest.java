package com.acrylic.outbound.temporary.review;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TemporarySourceBindingQueriesTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final TemporarySourceBindingQueries queries=new TemporarySourceBindingQueries(jdbc);
    private final UUID device=UUID.randomUUID();

    @Test void extraRowProducesContinuationFromLastReturnedItem() {
        var rows=rows(3); stub(rows);
        var page=queries.mine(device,"",2);
        assertThat(page.items()).containsExactlyElementsOf(rows.subList(0,2));
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isEqualTo(rows.get(1).path("scopeCursor").asText());
    }

    @Test void exactlyFullPageEndsWithoutContinuation() {
        var rows=rows(2); stub(rows);
        var page=queries.mine(device,"",2);
        assertThat(page.items()).containsExactlyElementsOf(rows);
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test void emptyPageEndsWithoutContinuation() {
        stub(List.of());
        var page=queries.mine(device,"",2);
        assertThat(page.items()).isEmpty();
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test void malformedCursorNeverQueriesDatabase() {
        assertThatThrownBy(()->queries.mine(device,"not-a-uuid",2)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(jdbc);
    }

    @Test void invalidPageSizeNeverQueriesDatabase() {
        for(int limit:List.of(0,201))
            assertThatThrownBy(()->queries.mine(device,"",limit)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(jdbc);
    }

    private void stub(List<ObjectNode> rows) {
        when(jdbc.query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<ObjectNode>>any(),
            any(byte[].class),any(byte[].class),anyInt())).thenReturn(rows);
    }

    private List<ObjectNode> rows(int count) {
        return IntStream.range(0,count).mapToObj(i->SubmissionJson.object()
            .put("scopeCursor",new UUID(0,count-i).toString())).toList();
    }
}
