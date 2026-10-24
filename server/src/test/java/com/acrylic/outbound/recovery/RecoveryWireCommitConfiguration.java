package com.acrylic.outbound.recovery;

import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import static org.assertj.core.api.Assertions.assertThat;

/** Explicit child-worker test source; never scanned or configured by the product. */
@TestConfiguration(proxyBeanMethods = false)
public class RecoveryWireCommitConfiguration {
    @Bean static BeanPostProcessor recoveryWireObserverPool(ConfigurableListableBeanFactory factory, Environment environment) {
        Path home = Path.of(environment.getRequiredProperty("outbound.test.wire-commit-home"));
        assertThat(home.getFileName().toString()).isEqualTo("restored-bound-wire-commit");
        assertThat(environment.getProperty("spring.main.web-application-type")).isEqualTo("none");
        return new BeanPostProcessor() {
            @Override public Object postProcessAfterInitialization(Object bean, String name) {
                if (!name.equals(RecoveryAdmissionConfiguration.MIGRATION_DATASOURCE)) return bean;
                assertThat(bean).isInstanceOf(com.zaxxer.hikari.HikariDataSource.class);
                return new RecoveryWireCommitDataSource((DataSource) bean,
                    new RecoveryWireCommitBoundary(home, () -> factory.getBean(RecoveryOperationRegistry.class)));
            }
        };
    }
}
