package com.kimsooin77.sync.integration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import com.kimsooin77.sync.simulation.groupware.MockGroupwareProperties;

import java.net.http.HttpClient;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({GroupwareProperties.class, IntegrationWorkerProperties.class,
        MockGroupwareProperties.class})
public class IntegrationConfiguration {

    @Bean
    Clock integrationClock() {
        return Clock.systemUTC();
    }

    @Bean
    RestClient groupwareRestClient(GroupwareProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
