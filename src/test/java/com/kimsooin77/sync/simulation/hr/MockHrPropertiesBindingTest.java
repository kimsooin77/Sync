package com.kimsooin77.sync.simulation.hr;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MockHrPropertiesBindingTest {
    @Test
    void bindsPartialInvalidStartupScenarioFromKebabCaseProperty() {
        new ApplicationContextRunner()
                .withUserConfiguration(MockHrConfiguration.class)
                .withPropertyValues("app.mock.hr.enabled=true", "app.mock.hr.scenario=partial-invalid")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(MockHrScenarioState.class).current())
                            .isEqualTo(MockHrProperties.Scenario.PARTIAL_INVALID);
                });
    }
}
