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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        dropSyncJobFailureTrigger();
        integrationTaskRepository.deleteAllInBatch();
        auditLogRepository.deleteAllInBatch();
        syncItemRepository.deleteAllInBatch();
        syncJobRepository.deleteAllInBatch();
        employeeRepository.deleteAllInBatch();
        HR_SERVER.respond(200, initialHrResponse());
    }

    @AfterEach
    void cleanSyncJobFailureTrigger() {
        dropSyncJobFailureTrigger();
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
    void rejectsConcurrentSyncBeforeCreatingSecondJobAndAllowsNextRunAfterCompletion() throws Exception {
        CountDownLatch hrRequestEntered = new CountDownLatch(1);
        CountDownLatch releaseHrResponse = new CountDownLatch(1);
        HR_SERVER.observeRequests(() -> {
            hrRequestEntered.countDown();
            try {
                if (!releaseHrResponse.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("test did not release the HR response");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
        });

        CompletableFuture<ResponseEntity<SyncJobResponse>> firstRun = CompletableFuture.supplyAsync(this::createJob);
        try {
            assertThat(hrRequestEntered.await(5, TimeUnit.SECONDS)).isTrue();
            ResponseEntity<String> rejected = httpClient().post().uri("/api/sync-jobs")
                    .retrieve()
                    .onStatus(status -> status.value() == HttpStatus.CONFLICT.value(), (request, response) -> { })
                    .toEntity(String.class);

            assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(rejected.getBody()).contains("SYNC_ALREADY_RUNNING");
            assertThat(syncJobRepository.count()).isEqualTo(1);
        } finally {
            releaseHrResponse.countDown();
        }

        assertThat(firstRun.get(10, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(syncJobRepository.count()).isEqualTo(1);

        HR_SERVER.respond(200, initialHrResponse());
        ResponseEntity<SyncJobResponse> nextRun = createJob();
        assertThat(nextRun.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(syncJobRepository.count()).isEqualTo(2);
    }

    @Test
    void releasesGuardWhenSyncJobCreationFailsBeforeHrCall() {
        jdbcTemplate.execute("""
                CREATE FUNCTION test_reject_sync_job_insert() RETURNS trigger
                LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE EXCEPTION 'test rejects sync job creation' USING ERRCODE = '58000';
                END;
                $$
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER test_reject_sync_job_insert
                BEFORE INSERT ON sync_job
                FOR EACH ROW EXECUTE FUNCTION test_reject_sync_job_insert()
                """);

        ResponseEntity<String> failed = httpClient().post().uri("/api/sync-jobs").retrieve()
                .onStatus(status -> status.is5xxServerError(), (request, response) -> { })
                .toEntity(String.class);

        assertThat(failed.getStatusCode().is5xxServerError()).isTrue();
        assertThat(syncJobRepository.count()).isZero();
        dropSyncJobFailureTrigger();

        ResponseEntity<SyncJobResponse> nextRun = createJob();
        assertThat(nextRun.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(syncJobRepository.count()).isEqualTo(1);
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

        HR_SERVER.respond(200, initialHrResponse());
        ResponseEntity<SyncJobResponse> nextRun = createJob();
        assertThat(nextRun.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(nextRun.getBody().status()).isEqualTo(SyncJobStatus.COMPLETED);
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

    private void dropSyncJobFailureTrigger() {
        if (jdbcTemplate == null) return;
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS test_reject_sync_job_insert ON sync_job");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS test_reject_sync_job_insert()");
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
