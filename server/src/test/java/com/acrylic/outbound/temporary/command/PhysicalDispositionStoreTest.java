package com.acrylic.outbound.temporary.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PhysicalDispositionStoreTest {
    @Test void rejectsManifestOrderBeforeWriting() {
        var jdbc = mock(JdbcTemplate.class);
        var store = new PhysicalDispositionStore(jdbc);
        UUID a=UUID.randomUUID(), b=UUID.randomUUID();
        var verified = new PhysicalDispositionVerification.Verified(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1,
            UUID.randomUUID(),UUID.randomUUID(),"a".repeat(64),1,1,List.of(a,b));
        var error = org.assertj.core.api.Assertions.catchThrowable(() -> store.apply(verified,"a".repeat(64),1,List.of(b,a),Instant.now()));
        assertThat(error).isInstanceOf(com.acrylic.outbound.common.BusinessException.class);
        verifyNoInteractions(jdbc);
    }
}
