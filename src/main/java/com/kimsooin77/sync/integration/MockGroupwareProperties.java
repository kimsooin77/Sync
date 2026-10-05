package com.kimsooin77.sync.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mock.groupware")
public record MockGroupwareProperties(boolean enabled) {
}
