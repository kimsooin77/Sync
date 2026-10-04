package com.kimsooin77.sync.sync.hr;

import com.kimsooin77.sync.sync.HrEmployeeResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HrEmployeeClientTest {

    private HttpStubServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpStubServer.start();
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void fetchesAndMapsExternalEmployeeNameContract() {
        server.respond(200, """
                [{"employee_no":"E1001","employee_name":"Kim","email":null,
                  "department_code":"DEV01","employment_status":"ACTIVE"}]
                """);

        List<HrEmployeeResponse> responses = client(Duration.ofSeconds(1)).fetchEmployees();

        assertThat(responses).containsExactly(new HrEmployeeResponse(
                "E1001", "Kim", null, "DEV01", "ACTIVE"));
    }

    @Test
    void mapsHttpFailuresToSafeHrErrors() {
        server.respond(500, "internal HR details must not be exposed");

        assertThatThrownBy(() -> client(Duration.ofSeconds(1)).fetchEmployees())
                .isInstanceOfSatisfying(HrEmployeeClientException.class, failure -> {
                    assertThat(failure.getErrorCode()).isEqualTo(HrClientErrorCode.HR_HTTP_ERROR);
                    assertThat(failure.getMessage()).doesNotContain("internal HR details");
                });
    }

    @Test
    void mapsMalformedWholeResponseToInvalidResponse() {
        server.respond(200, "[{\"employee_no\":]");

        assertThatThrownBy(() -> client(Duration.ofSeconds(1)).fetchEmployees())
                .isInstanceOfSatisfying(HrEmployeeClientException.class, failure ->
                        assertThat(failure.getErrorCode()).isEqualTo(HrClientErrorCode.HR_RESPONSE_INVALID));
    }

    @Test
    void mapsReadTimeoutToHrTimeout() {
        server.respond(200, "[]");
        server.delayResponse(400);

        assertThatThrownBy(() -> client(Duration.ofMillis(50)).fetchEmployees())
                .isInstanceOfSatisfying(HrEmployeeClientException.class, failure ->
                        assertThat(failure.getErrorCode()).isEqualTo(HrClientErrorCode.HR_TIMEOUT));
    }

    @Test
    void mapsConnectionRefusalToConnectionError() {
        server.stop();

        assertThatThrownBy(() -> client(Duration.ofSeconds(1)).fetchEmployees())
                .isInstanceOfSatisfying(HrEmployeeClientException.class, failure ->
                        assertThat(failure.getErrorCode()).isEqualTo(HrClientErrorCode.HR_CONNECTION_ERROR));
    }

    private HrEmployeeClient client(Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(250))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return new HrEmployeeClient(RestClient.builder()
                .baseUrl(server.baseUrl())
                .requestFactory(requestFactory)
                .build());
    }
}
