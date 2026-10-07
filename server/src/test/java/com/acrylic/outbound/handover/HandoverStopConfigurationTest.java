package com.acrylic.outbound.handover;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class HandoverStopConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(HandoverStopController.class, HandoverStopStore.class)
        .withBean(HandoverStopService.class, () -> mock(HandoverStopService.class))
        .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
        .withBean(com.acrylic.outbound.submissions.SubmissionStore.class, () -> mock(com.acrylic.outbound.submissions.SubmissionStore.class))
        .withBean(PersistenceExceptionTranslationPostProcessor.class);

    @Test void defaultGateHasNoStopControllerAndRepositorySupportsExceptionTranslation() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(HandoverStopController.class);
            assertThat(AopUtils.isAopProxy(context.getBean(HandoverStopStore.class))).isTrue();
        });
    }

    @Test void explicitTestGateInstallsController() {
        runner.withPropertyValues("outbound.handover.commands.enabled=true").run(context ->
            assertThat(context).hasNotFailed().hasSingleBean(HandoverStopController.class));
    }
}
