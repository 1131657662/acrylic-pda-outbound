package com.acrylic.outbound.temporary.continuation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class TemporaryRoundClosedTest {
    private final ApplicationContextRunner context=new ApplicationContextRunner()
        .withUserConfiguration(TemporaryRoundController.class,TemporaryRoundIntake.class);
    @Test void missingFlagDoesNotRegisterIntakeOrHttpController() {
        context.run(c->assertThat(c).doesNotHaveBean(TemporaryRoundController.class).doesNotHaveBean(TemporaryRoundIntake.class));
    }
    @Test void explicitFalseDoesNotRegisterIntakeOrHttpController() {
        context.withPropertyValues("outbound.temporary.continuation-intake.enabled=false")
            .run(c->assertThat(c).doesNotHaveBean(TemporaryRoundController.class).doesNotHaveBean(TemporaryRoundIntake.class));
    }
}
