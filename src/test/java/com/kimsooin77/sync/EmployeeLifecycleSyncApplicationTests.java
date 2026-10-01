package com.kimsooin77.sync;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EmployeeLifecycleSyncApplicationTests {

    @LocalServerPort
    private int port;

    @Test
    void startsEmbeddedWebServer() {
        assertThat(port).isPositive();
    }
}