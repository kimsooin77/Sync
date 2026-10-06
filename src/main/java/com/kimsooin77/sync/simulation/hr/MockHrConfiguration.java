package com.kimsooin77.sync.simulation.hr;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MockHrProperties.class)
public class MockHrConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "app.mock.hr", name = "enabled", havingValue = "true")
    MockHrScenarioState mockHrScenarioState(MockHrProperties properties) {
        return new MockHrScenarioState(properties.scenario());
    }
}
