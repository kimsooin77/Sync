package com.kimsooin77.sync.integration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.UUID;

@Component
public class GroupwareClient {

    private final RestClient restClient;

    public GroupwareClient(@Qualifier("groupwareRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public void send(IntegrationAction action, GroupwareAccountRequest request, UUID idempotencyKey) {
        try {
            switch (action) {
                case CREATE_ACCOUNT -> restClient.post()
                        .uri("/mock/groupware/accounts")
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .body(request)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (httpRequest, response) -> {
                            throw new GroupwareClientException("GROUPWARE_HTTP_ERROR",
                                    "Groupware returned an HTTP error", response.getStatusCode().value(), null);
                        })
                        .toBodilessEntity();
                case UPDATE_ACCOUNT -> restClient.put()
                        .uri("/mock/groupware/accounts/{employeeNo}", request.employeeNo())
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .body(request)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (httpRequest, response) -> {
                            throw new GroupwareClientException("GROUPWARE_HTTP_ERROR",
                                    "Groupware returned an HTTP error", response.getStatusCode().value(), null);
                        })
                        .toBodilessEntity();
                case DISABLE_ACCOUNT -> restClient.patch()
                        .uri("/mock/groupware/accounts/{employeeNo}/disable", request.employeeNo())
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .body(request)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (httpRequest, response) -> {
                            throw new GroupwareClientException("GROUPWARE_HTTP_ERROR",
                                    "Groupware returned an HTTP error", response.getStatusCode().value(), null);
                        })
                        .toBodilessEntity();
            }
        } catch (GroupwareClientException expectedFailure) {
            if (action == IntegrationAction.DISABLE_ACCOUNT && Integer.valueOf(404).equals(
                    expectedFailure.getHttpStatus())) {
                return;
            }
            throw expectedFailure;
        } catch (RestClientResponseException httpFailure) {
            throw new GroupwareClientException("GROUPWARE_HTTP_ERROR", "Groupware returned an HTTP error",
                    httpFailure.getStatusCode().value(), httpFailure);
        } catch (ResourceAccessException connectionFailure) {
            String code = hasCause(connectionFailure, HttpTimeoutException.class)
                    || hasCause(connectionFailure, SocketTimeoutException.class)
                    ? "GROUPWARE_TIMEOUT" : "GROUPWARE_CONNECTION_ERROR";
            throw new GroupwareClientException(code, "Groupware request could not be completed", null,
                    connectionFailure);
        } catch (RestClientException responseFailure) {
            throw new GroupwareClientException("GROUPWARE_RESPONSE_INVALID",
                    "Groupware response could not be processed", null, responseFailure);
        }
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> causeType) {
        Throwable current = failure;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
