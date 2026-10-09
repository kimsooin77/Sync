package com.kimsooin77.sync;

import com.kimsooin77.sync.integration.GroupwareProperties;
import com.kimsooin77.sync.integration.IntegrationConfiguration;
import com.kimsooin77.sync.sync.hr.HrEmployeeClientConfiguration;
import com.kimsooin77.sync.sync.hr.HrEmployeeClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(HrEmployeeClientConfiguration.class, IntegrationConfiguration.class);

    @Test
    void defaultsMockClientsToLoopbackOnTheConfiguredServerPort() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("server.port")).isEqualTo("8080");
            assertThat(context.getBean(HrEmployeeClientProperties.class).baseUrl())
                    .isEqualTo("http://127.0.0.1:8080");
            assertThat(context.getBean(GroupwareProperties.class).baseUrl())
                    .isEqualTo("http://127.0.0.1:8080");
        });
    }

    @Test
    void usesPortEnvironmentVariableForTheServerAndBothInternalMockClients() {
        contextRunner.withPropertyValues("PORT=31877").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("server.port")).isEqualTo("31877");
            assertThat(context.getBean(HrEmployeeClientProperties.class).baseUrl())
                    .isEqualTo("http://127.0.0.1:31877");
            assertThat(context.getBean(GroupwareProperties.class).baseUrl())
                    .isEqualTo("http://127.0.0.1:31877");
        });
    }

    @Test
    void usesServerPortEnvironmentFallbackWhenPortIsAbsent() {
        contextRunner.withPropertyValues("SERVER_PORT=29000").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("server.port")).isEqualTo("29000");
            assertThat(context.getBean(HrEmployeeClientProperties.class).baseUrl())
                    .isEqualTo("http://127.0.0.1:29000");
            assertThat(context.getBean(GroupwareProperties.class).baseUrl())
                    .isEqualTo("http://127.0.0.1:29000");
        });
    }

    @Test
    void explicitExternalClientUrlsStillOverrideInternalDefaults() {
        contextRunner.withPropertyValues(
                "PORT=31877",
                "HR_BASE_URL=https://hr.example.test",
                "GROUPWARE_BASE_URL=https://groupware.example.test"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(HrEmployeeClientProperties.class).baseUrl())
                    .isEqualTo("https://hr.example.test");
            assertThat(context.getBean(GroupwareProperties.class).baseUrl())
                    .isEqualTo("https://groupware.example.test");
        });
    }
}
