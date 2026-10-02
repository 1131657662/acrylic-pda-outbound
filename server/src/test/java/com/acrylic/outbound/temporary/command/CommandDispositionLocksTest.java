package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommandDispositionLocksTest {
    @Test void ordinaryCommandRequiresItsMappedSlot() {
        var fixture = fixture();
        assertThatThrownBy(() -> fixture.locks().source(fixture.hint())).isInstanceOf(BusinessException.class);
    }

    @Test void physicalDispositionAcceptsAnIndependentMappedSourceWithoutASlot() {
        var fixture = fixture();
        assertThatCode(() -> fixture.locks().sourceDisposition(fixture.hint())).doesNotThrowAnyException();
    }

    private Fixture fixture() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(call ->
            call.getArgument(0).toString().contains("order_submission_slot") ? List.of() : List.of(Map.of("id", "locked")));
        var locks = new CommandLocks(null, null, null, null, jdbc);
        var hint = SubmissionJson.object();
        for (String field : List.of("authorizationId", "creatorDeviceId", "clientOrderId", "scopeId", "canonicalOrderId", "submissionId"))
            hint.put(field, UUID.randomUUID().toString());
        return new Fixture(locks, new CommandContext(hint));
    }

    private record Fixture(CommandLocks locks, CommandContext hint) { }
}
