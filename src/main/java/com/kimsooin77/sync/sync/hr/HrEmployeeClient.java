package com.kimsooin77.sync.sync.hr;

import com.kimsooin77.sync.sync.HrEmployeeResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;

@Component
public class HrEmployeeClient {

    private static final ParameterizedTypeReference<List<HrEmployeeResponse>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    public HrEmployeeClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public List<HrEmployeeResponse> fetchEmployees() {
        try {
            List<HrEmployeeResponse> responses = restClient.get()
                    .uri("/mock/hr/employees")
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new HrEmployeeClientException(
                                HrClientErrorCode.HR_HTTP_ERROR,
                                "HR 시스템이 HTTP 오류를 반환했습니다.");
                    })
                    .body(RESPONSE_TYPE);
            if (responses == null) {
                throw new HrEmployeeClientException(
                        HrClientErrorCode.HR_RESPONSE_INVALID,
                        "HR 시스템 응답 본문이 비어 있습니다.");
            }
            return responses;
        } catch (HrEmployeeClientException expectedFailure) {
            throw expectedFailure;
        } catch (RestClientResponseException httpFailure) {
            throw new HrEmployeeClientException(
                    HrClientErrorCode.HR_HTTP_ERROR,
                    "HR 시스템이 HTTP 오류를 반환했습니다.",
                    httpFailure);
        } catch (ResourceAccessException connectionFailure) {
            if (hasCause(connectionFailure, HttpTimeoutException.class)
                    || hasCause(connectionFailure, SocketTimeoutException.class)) {
                throw new HrEmployeeClientException(
                        HrClientErrorCode.HR_TIMEOUT,
                        "HR 시스템 응답 시간이 초과되었습니다.",
                        connectionFailure);
            }
            throw new HrEmployeeClientException(
                    HrClientErrorCode.HR_CONNECTION_ERROR,
                    "HR 시스템에 연결할 수 없습니다.",
                    connectionFailure);
        } catch (RestClientException responseFailure) {
            if (hasCause(responseFailure, JacksonException.class)) {
                throw new HrEmployeeClientException(
                        HrClientErrorCode.HR_RESPONSE_INVALID,
                        "HR 시스템 응답을 직원 목록으로 읽을 수 없습니다.",
                        responseFailure);
            }
            throw new HrEmployeeClientException(
                    HrClientErrorCode.HR_RESPONSE_INVALID,
                    "HR 시스템 응답을 처리할 수 없습니다.",
                    responseFailure);
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
