package com.kimsooin77.sync.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.groupware")
public record GroupwareProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
