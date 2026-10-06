package com.kimsooin77.sync.api.sync;

import com.kimsooin77.sync.audit.AuditLogRepository;
import com.kimsooin77.sync.AdminHttpSession;
import com.kimsooin77.sync.employee.EmployeeRepository;
import com.kimsooin77.sync.employee.PostgreSqlTestConfiguration;
import com.kimsooin77.sync.integration.IntegrationTaskRepository;
import com.kimsooin77.sync.sync.SyncItemRepository;
import com.kimsooin77.sync.sync.SyncJobRepository;
import com.kimsooin77.sync.sync.SyncJobStatus;
import com.kimsooin77.sync.sync.hr.HttpStubServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.mock.hr.enabled=true",
                "app.mock.hr.scenario=initial",
                "app.hr.read-timeout=2s"
        })
@Import(PostgreSqlTestConfiguration.class)
class SyncJobControllerIntegrationTest {

    private static final HttpStubServer HR_SERVER = startHrServer();

    @LocalServerPort
    private int port;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private SyncJobRepository syncJobRepository;

    @Autowired
    private SyncItemRepository syncItemRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private IntegrationTaskRepository integrationTaskRepository;

    @DynamicPropertySource
    static void configureHrClient(DynamicPropertyRegistry registry) {
        registry.add("app.hr.base-url", HR_SERVER::baseUrl);
    }

    @AfterAll
    static void stopHrServer() {
        HR_SERVER.close();
    }

    @BeforeEach
    void resetDatabaseAndHrResponse() {
        integrationTaskRepository.deleteAllInBatch();
        auditLogRepository.deleteAllInBatch();
        syncItemRepository.deleteAllInBatch();
        syncJobRepository.deleteAllInBatch();
        employeeRepository.deleteAllInBatch();
        HR_SERVER.respond(200, initialHrResponse());
    }

    @Test
    void mockHrEndpointUsesTheExternalEmployeeNameField() {
        ResponseEntity<String> response = httpClient().get()
                .uri("/mock/hr/employees")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"employee_name\":\"김수인\"")
                .doesNotContain("\"name\":");
    }

    @Test
    void postCreatesJobBeforeCallingHrAndSynchronizesEmployees() {
        HR_SERVER.observeRequests(() -> assertThat(syncJobRepository.findAll())
                .anySatisfy(job -> assertThat(job.getStatus()).isEqualTo(SyncJobStatus.RUNNING)));

        ResponseEntity<SyncJobResponse> response = createJob();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).hasPath("/api/sync-jobs/" + response.getBody().syncJobId());
        assertThat(response.getBody()).satisfies(job -> {
            assertThat(job.status()).isEqualTo(SyncJobStatus.COMPLETED);
            assertThat(job.totalCount()).isEqualTo(3);
            assertThat(job.insertedCount()).isEqualTo(3);
        });
        ResponseEntity<SyncJobResponse> fetched = getJob(response.getBody().syncJobId());
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody()).satisfies(job -> {
            assertThat(job.syncJobId()).isEqualTo(response.getBody().syncJobId());
            assertThat(job.status()).isEqualTo(response.getBody().status());
            assertThat(job.totalCount()).isEqualTo(response.getBody().totalCount());
            assertThat(job.insertedCount()).isEqualTo(response.getBody().insertedCount());
            assertThat(job.updatedCount()).isEqualTo(response.getBody().updatedCount());
            assertThat(job.skippedCount()).isEqualTo(response.getBody().skippedCount());
            assertThat(job.failedCount()).isEqualTo(response.getBody().failedCount());
            assertThat(job.failureCode()).isEqualTo(response.getBody().failureCode());
            assertThat(job.failureMessage()).isEqualTo(response.getBody().failureMessage());
            assertThat(Duration.between(job.finishedAt(), response.getBody().finishedAt()).abs())
                    .isLessThanOrEqualTo(Duration.ofMillis(1));
        });
        assertThat(HR_SERVER.requestObserverFailure()).isNull();
        assertThat(employeeRepository.count()).isEqualTo(3);
    }

    @Test
    void getReturnsNotFoundForUnknownSyncJob() {
        ResponseEntity<String> response = httpClient().get().uri("/api/sync-jobs/{id}", Long.MAX_VALUE)
                .retrieve().onStatus(status -> status.value() == HttpStatus.NOT_FOUND.value(),
                        (request, failure) -> { }).toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("SYNC_JOB_NOT_FOUND");
        assertThat(employeeRepository.count()).isZero();
        assertThat(syncItemRepository.count()).isZero();
    }

    @Test
    void repeatedPostSkipsExistingEmployees() {
        ResponseEntity<SyncJobResponse> first = createJob();
        ResponseEntity<SyncJobResponse> second = createJob();

        assertThat(first.getBody().insertedCount()).isEqualTo(3);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody()).satisfies(job -> {
            assertThat(job.status()).isEqualTo(SyncJobStatus.COMPLETED);
            assertThat(job.skippedCount()).isEqualTo(3);
        });
        assertThat(employeeRepository.count()).isEqualTo(3);
        assertThat(auditLogRepository.count()).isEqualTo(3);
        assertThat(integrationTaskRepository.count()).isEqualTo(3);
    }

    @Test
    void invalidEmployeeStatusFailsOnlyThatRow() {
        HR_SERVER.respond(200, """
                [
                  {"employee_no":"E1","employee_name":"Valid One","email":null,"department_code":null,"employment_status":"ACTIVE"},
                  {"employee_no":"E2","employee_name":"Invalid","email":null,"department_code":null,"employment_status":"UNKNOWN"},
                  {"employee_no":"E3","employee_name":"Valid Three","email":null,"department_code":null,"employment_status":"ON_LEAVE"}
                ]
                """);

        ResponseEntity<SyncJobResponse> response = createJob();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).satisfies(job -> {
            assertThat(job.status()).isEqualTo(SyncJobStatus.COMPLETED_WITH_ERRORS);
            assertThat(job.totalCount()).isEqualTo(3);
            assertThat(job.insertedCount()).isEqualTo(2);
            assertThat(job.failedCount()).isEqualTo(1);
        });
        assertThat(employeeRepository.count()).isEqualTo(2);
        assertThat(syncItemRepository.count()).isEqualTo(3);
    }

    @Test
    void httpFailureReturnsCreatedFailedJobWithoutEmployeeRows() {
        HR_SERVER.respond(503, "private upstream diagnostics");

        ResponseEntity<SyncJobResponse> response = createJob();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).satisfies(job -> {
            assertThat(job.status()).isEqualTo(SyncJobStatus.FAILED);
            assertThat(job.totalCount()).isZero();
            assertThat(job.failureCode()).isEqualTo("HR_HTTP_ERROR");
            assertThat(job.failureMessage()).doesNotContain("private upstream");
            assertThat(job.finishedAt()).isNotNull();
        });
        assertNoEmployeeResults();
        assertThat(syncJobRepository.count()).isEqualTo(1);
    }

    @Test
    void readTimeoutReturnsFailedJobWithoutEmployeeRows() {
        HR_SERVER.respond(200, initialHrResponse());
        HR_SERVER.delayResponse(3_000);

        ResponseEntity<SyncJobResponse> response = createJob();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).satisfies(job -> {
            assertThat(job.status()).isEqualTo(SyncJobStatus.FAILED);
            assertThat(job.failureCode()).isEqualTo("HR_TIMEOUT");
        });
        assertNoEmployeeResults();
    }

    @Test
    void malformedWholeResponseReturnsFailedJobWithoutEmployeeRows() {
        HR_SERVER.respond(200, "[{\"employee_no\":]");

        ResponseEntity<SyncJobResponse> response = createJob();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).satisfies(job -> {
            assertThat(job.status()).isEqualTo(SyncJobStatus.FAILED);
            assertThat(job.failureCode()).isEqualTo("HR_RESPONSE_INVALID");
        });
        assertNoEmployeeResults();
    }

    private ResponseEntity<SyncJobResponse> createJob() {
        return httpClient().post()
                .uri("/api/sync-jobs")
                .retrieve()
                .toEntity(SyncJobResponse.class);
    }

    private ResponseEntity<SyncJobResponse> getJob(Long id) {
        return httpClient().get()
                .uri("/api/sync-jobs/{id}", id)
                .retrieve()
                .onStatus(status -> status.value() == HttpStatus.NOT_FOUND.value(),
                        (request, response) -> { })
                .toEntity(SyncJobResponse.class);
    }

    private void assertNoEmployeeResults() {
        assertThat(employeeRepository.count()).isZero();
        assertThat(syncItemRepository.count()).isZero();
        assertThat(auditLogRepository.count()).isZero();
        assertThat(integrationTaskRepository.count()).isZero();
    }

    private RestClient httpClient() {
        return AdminHttpSession.login("http://localhost:" + port).client();
    }

    private static String initialHrResponse() {
        return """
                [
                  {"employee_no":"E1001","employee_name":"김수인","email":"sooin@company.com","department_code":"DEV01","employment_status":"ACTIVE"},
                  {"employee_no":"E1002","employee_name":"홍길동","email":"hong@company.com","department_code":"DEV01","employment_status":"ACTIVE"},
                  {"employee_no":"E1003","employee_name":"이민지","email":"minji@company.com","department_code":"OPS01","employment_status":"ON_LEAVE"}
                ]
                """;
    }

    private static HttpStubServer startHrServer() {
        try {
            return HttpStubServer.start();
        } catch (IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }
}
