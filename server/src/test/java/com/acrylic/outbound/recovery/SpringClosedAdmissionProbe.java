package com.acrylic.outbound.recovery;

import java.sql.Connection;
import javax.sql.DataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import static org.assertj.core.api.Assertions.*;

/** Observes a real primary borrow during bean creation; no alternative datasource or HTTP route. */
@TestConfiguration(proxyBeanMethods = false)
public class SpringClosedAdmissionProbe {
    @Bean StartupBorrow closedAdmissionStartupBorrow(DataSource source, RecoveryOperationRegistry registry)
            throws Exception {
        var token = registry.bound(Thread.currentThread());
        assertThat(token).isNotNull();
        assertThat(token.kind()).isEqualTo(RecoveryOperationKind.INITIALIZATION);
        assertThat(registry.liveRoots()).isEqualTo(1);
        try (Connection connection = source.getConnection(); var query = connection.createStatement();
                var result = query.executeQuery("SELECT CONNECTION_ID()")) {
            assertThat(result.next()).isTrue();
            assertThat(registry.live(token).openSubOperations()).isEqualTo(1);
            return new StartupBorrow(result.getLong(1), token.operationId().toString());
        }
    }

    record StartupBorrow(long connectionId, String operationId) { }
}
