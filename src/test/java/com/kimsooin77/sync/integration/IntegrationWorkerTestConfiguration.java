package com.kimsooin77.sync.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;

@TestConfiguration(proxyBeanMethods = false)
class IntegrationWorkerTestConfiguration {

    @Bean
    @Primary
    MutableClock integrationTestClock() {
        return new MutableClock();
    }
}
