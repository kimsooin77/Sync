package com.kimsooin77.sync.api;

import com.kimsooin77.sync.employee.PostgreSqlTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.admin.username=test-admin",
                "app.admin.password-hash=$2a$10$yWaXqUtGASNN64T2jB3VQOxrgprZobIeSDMPAB47VZlOraKdZ2W7K",
                "springdoc.api-docs.enabled=false",
                "springdoc.swagger-ui.enabled=false"
        })
@Import(PostgreSqlTestConfiguration.class)
class SpaRoutingIntegrationTest {

    @LocalServerPort
    int port;

    @Test
    void healthIsPublicAndExposesOnlyOverallStatus() {
        RestClient client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        ResponseEntity<String> response = client.get().uri("/actuator/health").retrieve().toEntity(String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().replaceAll("\\s", "")).isEqualTo("{\"status\":\"UP\"}");
        assertThat(client.head().uri("/actuator/health").retrieve().toBodilessEntity()
                .getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void explicitSpaRoutesServeIndexForGetAndHead() {
        RestClient client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        for (String path : new String[]{"/", "/login", "/employees", "/integrations"}) {
            ResponseEntity<String> response = client.get().uri(path).retrieve()
                    .onStatus(HttpStatusCode::isError, (request, result) -> { })
                    .toEntity(String.class);
            assertThat(response.getStatusCode().is2xxSuccessful()).as(path).isTrue();
            assertThat(response.getBody()).as(path).contains("Employee Sync Console");
            assertThat(client.head().uri(path).retrieve()
                    .onStatus(HttpStatusCode::isError, (request, result) -> { })
                    .toBodilessEntity().getStatusCode().is2xxSuccessful()).as("HEAD " + path).isTrue();
        }
    }

    @Test
    void unknownAssetsAndProtectedOrExcludedRoutesNeverReceiveTheSpaDocument() {
        RestClient client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        for (String path : new String[]{
                "/assets/missing.js", "/api/missing", "/mock/missing", "/actuator/env", "/v3/api-docs"}) {
            ResponseEntity<String> response = client.get().uri(path).retrieve()
                    .onStatus(HttpStatusCode::isError, (request, result) -> { })
                    .toEntity(String.class);
            assertThat(response.getStatusCode().is2xxSuccessful()).as(path).isFalse();
            assertThat(response.getBody()).as(path).doesNotContain("Employee Sync Console");
        }
    }
}
