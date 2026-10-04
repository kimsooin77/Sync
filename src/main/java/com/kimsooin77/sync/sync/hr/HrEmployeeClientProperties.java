package com.kimsooin77.sync.sync.hr;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.hr")
public record HrEmployeeClientProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
}
