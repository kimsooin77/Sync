package com.kimsooin77.sync.simulation.hr;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MockHrProperties.class)
public class MockHrConfiguration {
}
