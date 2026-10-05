package com.kimsooin77.sync.integration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Objects;
import java.util.UUID;

@Component
public class GroupwareClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GroupwareClient(@Qualifier("groupwareRestClient") RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public GroupwareCallResult send(IntegrationAction action, GroupwareAccountRequest request, UUID idempotencyKey) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Groupware HTTP calls must run outside a database transaction");
        }
        try {
            return switch (action) {
                case CREATE_ACCOUNT -> execute(HttpMethod.POST, "/mock/groupware/accounts", action,
                        request, idempotencyKey);
                case UPDATE_ACCOUNT -> execute(HttpMethod.PUT, "/mock/groupware/accounts/{employeeNo}", action,
                        request, idempotencyKey, request.employeeNo());
                case DISABLE_ACCOUNT -> execute(HttpMethod.PATCH,
                        "/mock/groupware/accounts/{employeeNo}/disable", action, request, idempotencyKey,
                        request.employeeNo());
            };
        } catch (GroupwareClientException expectedFailure) {
            throw expectedFailure;
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

    private GroupwareCallResult execute(HttpMethod method, String uri, IntegrationAction action,
                                        GroupwareAccountRequest request, UUID idempotencyKey,
                                        Object... uriVariables) {
        return restClient.method(method)
                .uri(uri, uriVariables)
                .header("Idempotency-Key", idempotencyKey.toString())
                .body(request)
                .exchange((httpRequest, response) -> {
                    int status = response.getStatusCode().value();
                    byte[] responseBody;
                    try {
                        responseBody = response.getBody().readAllBytes();
                    } catch (IOException ioFailure) {
                        throw new GroupwareClientException("GROUPWARE_RESPONSE_INVALID",
                                "Groupware response could not be processed", status, ioFailure);
                    }
                    if (status >= 200 && status < 300) {
                        return new GroupwareCallResult(status);
                    }
                    if (action == IntegrationAction.DISABLE_ACCOUNT && status == 404
                            && isAccountNotFound(responseBody)) {
                        return new GroupwareCallResult(status);
                    }
                    String errorCode = status == 409 && isIdempotencyConflict(responseBody)
                            ? "IDEMPOTENCY_KEY_CONFLICT" : "GROUPWARE_HTTP_ERROR";
                    throw new GroupwareClientException(errorCode,
                            "Groupware returned an HTTP error", status, null);
                });
    }

    private boolean isAccountNotFound(byte[] responseBody) {
        try {
            GroupwareErrorResponse error = objectMapper.readValue(responseBody, GroupwareErrorResponse.class);
            return error != null && "ACCOUNT_NOT_FOUND".equals(error.code());
        } catch (JacksonException invalidBody) {
            return false;
        }
    }

    private boolean isIdempotencyConflict(byte[] responseBody) {
        try {
            GroupwareErrorResponse error = objectMapper.readValue(responseBody, GroupwareErrorResponse.class);
            return error != null && "IDEMPOTENCY_KEY_CONFLICT".equals(error.code());
        } catch (JacksonException invalidBody) {
            return false;
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
