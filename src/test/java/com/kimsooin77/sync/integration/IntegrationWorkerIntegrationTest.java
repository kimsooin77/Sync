package com.kimsooin77.sync.integration;

import com.kimsooin77.sync.audit.AuditLogRepository;
import com.kimsooin77.sync.AdminHttpSession;
import com.kimsooin77.sync.employee.EmployeeRepository;
import com.kimsooin77.sync.employee.EmploymentStatus;
import com.kimsooin77.sync.employee.PostgreSqlTestConfiguration;
import com.kimsooin77.sync.sync.EmployeeSyncService;
import com.kimsooin77.sync.sync.HrEmployeeResponse;
import com.kimsooin77.sync.sync.SyncItemRepository;
import com.kimsooin77.sync.sync.SyncJobRepository;
import com.kimsooin77.sync.simulation.groupware.MockGroupwareAccountStore;
import com.kimsooin77.sync.simulation.groupware.FailureSimulationRequest;
import com.kimsooin77.sync.simulation.groupware.FailureMode;
import com.kimsooin77.sync.api.integration.IntegrationTaskRetryResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.integration.worker.enabled=false",
                "app.mock.groupware.enabled=true",
                "app.mock.groupware.timeout-delay-ms=1500"
        })
@Import({PostgreSqlTestConfiguration.class, IntegrationWorkerTestConfiguration.class})
class IntegrationWorkerIntegrationTest {

    private static final GroupwareStubServer GROUPWARE_STUB = GroupwareStubServer.start();

    @DynamicPropertySource
    static void groupwareBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("app.groupware.base-url", GROUPWARE_STUB::baseUrl);
        registry.add("app.groupware.read-timeout", () -> "1s");
    }

    @Autowired
    private EmployeeSyncService employeeSyncService;

    @Autowired
    private IntegrationWorker integrationWorker;

    @Autowired
    private IntegrationTaskRepository integrationTaskRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private SyncItemRepository syncItemRepository;

    @Autowired
    private SyncJobRepository syncJobRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private MockGroupwareAccountStore accountStore;

    @Autowired
    private IntegrationAttemptRepository integrationAttemptRepository;

    @Autowired
    private IntegrationTaskTransactionService transactionService;

    @Autowired
    private GroupwareClient groupwareClient;

    @Autowired
    private MutableClock clock;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @LocalServerPort
    private int port;

    @BeforeEach
    void clearState() {
        clearDatabase();
        accountStore.clear();
        GROUPWARE_STUB.clear();
        clock.reset();
    }

    @AfterEach
    void cleanState() {
        clearDatabase();
        accountStore.clear();
        GROUPWARE_STUB.clear();
    }

    @AfterAll
    static void stopGroupwareStub() {
        GROUPWARE_STUB.close();
    }

    @Test
    void terminatedEmployeeReactivatedWithoutAccountIsCreatedByUpdateTask() {
        employeeSyncService.synchronize(java.util.List.of(
                hr("E9001", "Rehire", "rehire@company.com", "DEV01", "TERMINATED")));

        IntegrationTask terminationTask = integrationTaskRepository.findAll().getFirst();
        assertThat(terminationTask.getAction()).isEqualTo(IntegrationAction.DISABLE_ACCOUNT);
        assertThat(integrationWorker.runBatch()).isEqualTo(1);
        assertThat(integrationTaskRepository.findById(terminationTask.getId()).orElseThrow().getStatus())
                .isEqualTo(IntegrationTaskStatus.SUCCESS);
        assertThat(GROUPWARE_STUB.find("E9001")).isNull();

        employeeSyncService.synchronize(java.util.List.of(
                hr("E9001", "Rehire", "rehire@company.com", "DEV01", "ACTIVE")));
        IntegrationTask task = integrationTaskRepository.findAll().stream()
                .filter(candidate -> candidate.getAction() == IntegrationAction.UPDATE_ACCOUNT)
                .findFirst().orElseThrow();
        assertThat(task.getAction()).isEqualTo(IntegrationAction.UPDATE_ACCOUNT);
        assertThat(task.getStatus()).isEqualTo(IntegrationTaskStatus.PENDING);
        assertThat(GROUPWARE_STUB.find("E9001")).isNull();

        assertThat(integrationWorker.runBatch()).isEqualTo(1);

        IntegrationTask completed = integrationTaskRepository.findById(task.getId()).orElseThrow();
        GroupwareAccount account = GROUPWARE_STUB.find("E9001");
        assertThat(completed.getStatus()).isEqualTo(IntegrationTaskStatus.SUCCESS);
        assertThat(integrationTaskRepository.findById(terminationTask.getId()).orElseThrow().getStatus())
                .isEqualTo(IntegrationTaskStatus.SUCCESS);
        assertThat(account).isNotNull();
        assertThat(account.enabled()).isTrue();
        assertThat(account.employmentStatus()).isEqualTo(EmploymentStatus.ACTIVE.name());
        assertThat(account.email()).isEqualTo("rehire@company.com");
        assertThat(integrationWorker.runBatch()).isZero();
    }

    @Test
    void createIsDuplicateSafeAndPutCreatesAnEnabledAccount() {
        GroupwareAccountRequest request = new GroupwareAccountRequest(
                "E9002", "First", "first@company.com", "OPS01", "ACTIVE");
        RestClient client = localClient();

        client.post().uri("/mock/groupware/accounts").header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .body(request).retrieve().toBodilessEntity();
        assertThatThrownBy(() -> client.post().uri("/mock/groupware/accounts")
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .body(request).retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(RestClientResponseException.class, failure ->
                        assertThat(failure.getStatusCode().value()).isEqualTo(409));

        client.put().uri("/mock/groupware/accounts/{employeeNo}", "E9002")
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .body(new GroupwareAccountRequest("E9002", "First", null, "OPS01", "ACTIVE"))
                .retrieve().toBodilessEntity();

        client.put().uri("/mock/groupware/accounts/{employeeNo}", "E9003")
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .body(new GroupwareAccountRequest("E9003", "Created by PUT", null, null, "ACTIVE"))
                .retrieve().toBodilessEntity();

        assertThat(accountStore.find("E9002").enabled()).isTrue();
        assertThat(accountStore.find("E9002").email()).isNull();
        assertThat(accountStore.find("E9003")).satisfies(account -> {
            assertThat(account.name()).isEqualTo("Created by PUT");
            assertThat(account.enabled()).isTrue();
        });
    }

    @Test
    void mockGroupwareReplaysSameKeyAndRejectsDifferentRequest() {
        RestClient client = localClient();
        java.util.UUID key = java.util.UUID.randomUUID();
        GroupwareAccountRequest original = new GroupwareAccountRequest(
                "E9004", "Original", null, "OPS01", "ACTIVE");
        var first = client.post().uri("/mock/groupware/accounts").header("Idempotency-Key", key.toString())
                .body(original).retrieve().toBodilessEntity();
        var replay = client.post().uri("/mock/groupware/accounts").header("Idempotency-Key", key.toString())
                .body(original).retrieve().toBodilessEntity();
        assertThat(first.getStatusCode().value()).isEqualTo(201);
        assertThat(replay.getStatusCode().value()).isEqualTo(201);

        client.post().uri("/mock/groupware/accounts").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", key.toString())
                .body("{\"employment_status\":\"ACTIVE\",\"department_code\":\"OPS01\","
                        + "\"email\":null,\"name\":\"Original\",\"employee_no\":\"E9004\"}")
                .retrieve().toBodilessEntity();

        assertThatThrownBy(() -> client.post().uri("/mock/groupware/accounts")
                .header("Idempotency-Key", key.toString())
                .body(new GroupwareAccountRequest("E9004", "Changed", null, "OPS01", "ACTIVE"))
                .retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(RestClientResponseException.class, failure -> {
                    assertThat(failure.getStatusCode().value()).isEqualTo(409);
                    assertThat(failure.getResponseBodyAsString()).contains("IDEMPOTENCY_KEY_CONFLICT");
                });

        assertThatThrownBy(() -> client.put().uri("/mock/groupware/accounts/{employeeNo}", "E9004")
                .header("Idempotency-Key", key.toString()).body(original).retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(RestClientResponseException.class, failure -> {
                    assertThat(failure.getStatusCode().value()).isEqualTo(409);
                    assertThat(failure.getResponseBodyAsString()).contains("IDEMPOTENCY_KEY_CONFLICT");
                });
        assertThatThrownBy(() -> client.post().uri("/mock/groupware/accounts")
                .header("Idempotency-Key", key.toString())
                .body(new GroupwareAccountRequest("E9005", "Other employee", null, "OPS01", "ACTIVE"))
                .retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(RestClientResponseException.class, failure ->
                        assertThat(failure.getStatusCode().value()).isEqualTo(409));

        assertThat(accountStore.find("E9004").name()).isEqualTo("Original");
    }

    @Test
    void mockGroupwareRequiresUuidKeyAndReplaysMissingDisableAsSameBusinessResponse() {
        RestClient client = localClient();
        GroupwareAccountRequest request = new GroupwareAccountRequest(
                "E9005", "Former", null, null, "TERMINATED");
        assertThatThrownBy(() -> client.patch().uri("/mock/groupware/accounts/{employeeNo}/disable", "E9005")
                .body(request).retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(RestClientResponseException.class, failure ->
                        assertThat(failure.getStatusCode().value()).isEqualTo(400));
        assertThatThrownBy(() -> client.patch().uri("/mock/groupware/accounts/{employeeNo}/disable", "E9005")
                .header("Idempotency-Key", "not-a-uuid").body(request).retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(RestClientResponseException.class, failure ->
                        assertThat(failure.getStatusCode().value()).isEqualTo(400));

        java.util.UUID key = java.util.UUID.randomUUID();
        java.util.function.Supplier<RestClientResponseException> call = () -> {
            try {
                client.patch().uri("/mock/groupware/accounts/{employeeNo}/disable", "E9005")
                        .header("Idempotency-Key", key.toString()).body(request).retrieve().toBodilessEntity();
                throw new AssertionError("Expected the stable account-not-found response");
            } catch (RestClientResponseException expected) {
                return expected;
            }
        };
        RestClientResponseException first = call.get();
        RestClientResponseException replay = call.get();
        assertThat(first.getStatusCode().value()).isEqualTo(404);
        assertThat(replay.getStatusCode().value()).isEqualTo(404);
        assertThat(replay.getResponseBodyAsString()).isEqualTo(first.getResponseBodyAsString())
                .contains("ACCOUNT_NOT_FOUND");
    }

    @Test
    void failOnceBindsKeyToFirstRequestAndSuccessReplayPrecedesCurrentFailureMode() {
        RestClient client = localClient();
        GroupwareAccountRequest original = new GroupwareAccountRequest("E9030", "Bound", null, null, "ACTIVE");
        java.util.UUID key = java.util.UUID.randomUUID();
        client.put().uri("/mock/groupware/failure-simulation/{employeeNo}", "E9030")
                .body(new FailureSimulationRequest(FailureMode.FAIL_ONCE_THEN_SUCCESS, null))
                .retrieve().toBodilessEntity();

        assertThatThrownBy(() -> client.post().uri("/mock/groupware/accounts")
                .header("Idempotency-Key", key.toString()).body(original).retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(RestClientResponseException.class, failure ->
                        assertThat(failure.getStatusCode().value()).isEqualTo(500));

        assertThatThrownBy(() -> client.post().uri("/mock/groupware/accounts")
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .body(original).retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(RestClientResponseException.class, failure ->
                        assertThat(failure.getStatusCode().value()).isEqualTo(500));

        client.put().uri("/mock/groupware/failure-simulation/{employeeNo}", "E9030")
                .body(new FailureSimulationRequest(FailureMode.HTTP_500, null)).retrieve().toBodilessEntity();

        assertThatThrownBy(() -> client.post().uri("/mock/groupware/accounts")
                .header("Idempotency-Key", key.toString())
                .body(new GroupwareAccountRequest("E9031", "Changed", null, null, "ACTIVE"))
                .retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(RestClientResponseException.class, failure -> {
                    assertThat(failure.getStatusCode().value()).isEqualTo(409);
                    assertThat(failure.getResponseBodyAsString()).contains("IDEMPOTENCY_KEY_CONFLICT");
                });

        client.delete().uri("/mock/groupware/failure-simulation/{employeeNo}", "E9030")
                .retrieve().toBodilessEntity();
        client.post().uri("/mock/groupware/accounts").header("Idempotency-Key", key.toString())
                .body(original).retrieve().toBodilessEntity();
        client.put().uri("/mock/groupware/failure-simulation/{employeeNo}", "E9030")
                .body(new FailureSimulationRequest(FailureMode.HTTP_500, null)).retrieve().toBodilessEntity();
        var replay = client.post().uri("/mock/groupware/accounts").header("Idempotency-Key", key.toString())
                .body(original).retrieve().toBodilessEntity();

        assertThat(replay.getStatusCode().value()).isEqualTo(201);
        assertThat(accountStore.find("E9030")).isNotNull();
    }

    @Test
    void concurrentRequestsWithOneKeyExecuteMockBusinessOperationOnce() throws Exception {
        RestClient client = localClient();
        java.util.UUID key = java.util.UUID.randomUUID();
        GroupwareAccountRequest request = new GroupwareAccountRequest("E9038", "Concurrent", null, null, "ACTIVE");
        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(() -> client.post()
                .uri("/mock/groupware/accounts").header("Idempotency-Key", key.toString()).body(request)
                .retrieve().toBodilessEntity().getStatusCode().value());
        CompletableFuture<Integer> second = CompletableFuture.supplyAsync(() -> client.post()
                .uri("/mock/groupware/accounts").header("Idempotency-Key", key.toString()).body(request)
                .retrieve().toBodilessEntity().getStatusCode().value());

        assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(201);
        assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(201);
        assertThat(accountStore.find("E9038")).isNotNull();
    }

    @Test
    void failureSimulationApiValidatesRulesAndSupportsOverrides() {
        RestClient client = localClient();
        client.put().uri("/mock/groupware/failure-simulation/{employeeNo}", "e9032")
                .body(new FailureSimulationRequest(FailureMode.DELAY, 50L)).retrieve().toBodilessEntity();
        assertThat(client.get().uri("/mock/groupware/failure-simulation").retrieve().body(String.class))
                .contains("E9032", "DELAY", "50");

        assertThatThrownBy(() -> client.put().uri("/mock/groupware/failure-simulation/{employeeNo}", "E9032")
                .body(new FailureSimulationRequest(FailureMode.NORMAL, 5L)).retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(RestClientResponseException.class, failure ->
                        assertThat(failure.getStatusCode().value()).isEqualTo(400));
        assertThatThrownBy(() -> client.put().uri("/mock/groupware/failure-simulation/{employeeNo}", "E9032")
                .body(new FailureSimulationRequest(FailureMode.DELAY, 30_001L)).retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(RestClientResponseException.class, failure ->
                        assertThat(failure.getStatusCode().value()).isEqualTo(400));
        assertThat(client.delete().uri("/mock/groupware/failure-simulation/{employeeNo}", "E9032")
                .retrieve().toBodilessEntity().getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void failureModesAreIsolatedByEmployeeAndWorkerContinuesAfterFailures() {
        employeeSyncService.synchronize(List.of(
                hr("E9035", "Simulated failure", null, null, "ACTIVE"),
                hr("E9036", "Healthy employee", null, null, "ACTIVE")));
        List<IntegrationTask> tasks = integrationTaskRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(IntegrationTask::getId)).toList();
        RestClient client = localClient();
        client.put().uri("/mock/groupware/failure-simulation/{employeeNo}", "E9035")
                .body(new FailureSimulationRequest(FailureMode.HTTP_500, null)).retrieve().toBodilessEntity();
        GROUPWARE_STUB.forwardTo("http://localhost:" + port);

        assertThat(integrationWorker.runBatch()).isEqualTo(2);
        assertThat(integrationTaskRepository.findById(tasks.get(0).getId()).orElseThrow().getStatus())
                .isEqualTo(IntegrationTaskStatus.RETRY_WAIT);
        assertThat(integrationTaskRepository.findById(tasks.get(1).getId()).orElseThrow().getStatus())
                .isEqualTo(IntegrationTaskStatus.SUCCESS);
        assertThat(accountStore.find("E9035")).isNull();
        assertThat(accountStore.find("E9036")).isNotNull();

        client.put().uri("/mock/groupware/failure-simulation/{employeeNo}", "E9035")
                .body(new FailureSimulationRequest(FailureMode.DELAY, 20L)).retrieve().toBodilessEntity();
        clock.advanceSeconds(5);
        clock.advanceMillis(1);
        assertThat(integrationWorker.runBatch()).isOne();
        assertThat(integrationTaskRepository.findById(tasks.get(0).getId()).orElseThrow().getStatus())
                .isEqualTo(IntegrationTaskStatus.SUCCESS);
        assertThat(accountStore.find("E9035")).isNotNull();
    }

    @Test
    void timeoutFailureModeDoesNotRunGroupwareAccountOperation() {
        employeeSyncService.synchronize(List.of(hr("E9037", "Timeout", null, null, "ACTIVE")));
        IntegrationTask task = integrationTaskRepository.findAll().getFirst();
        localClient().put().uri("/mock/groupware/failure-simulation/{employeeNo}", "E9037")
                .body(new FailureSimulationRequest(FailureMode.TIMEOUT, null)).retrieve().toBodilessEntity();
        GROUPWARE_STUB.forwardTo("http://localhost:" + port);

        assertThat(integrationWorker.runBatch()).isOne();
        IntegrationTask waiting = integrationTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(waiting.getStatus()).isEqualTo(IntegrationTaskStatus.RETRY_WAIT);
        assertThat(waiting.getLastErrorCode()).isEqualTo("GROUPWARE_TIMEOUT");
        assertThat(accountStore.find("E9037")).isNull();
        assertThat(integrationAttemptRepository.findAllByIntegrationTask_IdOrderByAttemptNoAsc(task.getId()))
                .singleElement().satisfies(attempt ->
                        assertThat(attempt.getHttpStatus()).isNull());
    }

    @Test
    void manualRetryResetsOnlyRetryStateAndKeepsPayloadKeyAndAttempts() {
        employeeSyncService.synchronize(List.of(hr("E9033", "Manual retry", null, null, "ACTIVE")));
        IntegrationTask task = integrationTaskRepository.findAll().getFirst();
        GROUPWARE_STUB.failWithStatus("E9033", 400);
        assertThat(integrationWorker.runBatch()).isOne();
        IntegrationTask failed = integrationTaskRepository.findById(task.getId()).orElseThrow();
        var firstAttempt = integrationAttemptRepository.findAllByIntegrationTask_IdOrderByAttemptNoAsc(task.getId());
        assertThat(failed.getStatus()).isEqualTo(IntegrationTaskStatus.FAILED);

        IntegrationTaskRetryResponse response = localClient().post()
                .uri("/api/integration-tasks/{id}/retry", task.getId()).retrieve()
                .body(IntegrationTaskRetryResponse.class);
        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(IntegrationTaskStatus.PENDING);
        IntegrationTask pending = integrationTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(pending.getRetryCount()).isZero();
        assertThat(pending.getPayload()).isEqualTo(task.getPayload());
        assertThat(pending.getIdempotencyKey()).isEqualTo(task.getIdempotencyKey());
        assertThat(integrationAttemptRepository.findAllByIntegrationTask_IdOrderByAttemptNoAsc(task.getId()))
                .hasSize(firstAttempt.size());

        GROUPWARE_STUB.clearFailures();
        assertThat(integrationWorker.runBatch()).isOne();
        assertThat(integrationTaskRepository.findById(task.getId()).orElseThrow().getStatus())
                .isEqualTo(IntegrationTaskStatus.SUCCESS);
        assertThat(integrationAttemptRepository.findAllByIntegrationTask_IdOrderByAttemptNoAsc(task.getId()))
                .extracting(IntegrationAttempt::getAttemptNo).containsExactly(1, 2);
    }

    @Test
    void manualRetryRejectsMissingTaskNonFailedTaskAndDisallowedError() {
        RestClient client = localClient();
        assertThatThrownBy(() -> client.post().uri("/api/integration-tasks/999999/retry")
                .retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(RestClientResponseException.class, failure ->
                        assertThat(failure.getStatusCode().value()).isEqualTo(404));

        employeeSyncService.synchronize(List.of(hr("E9034", "Not failed", null, null, "ACTIVE")));
        IntegrationTask pending = integrationTaskRepository.findAll().getFirst();
        assertThatThrownBy(() -> client.post().uri("/api/integration-tasks/{id}/retry", pending.getId())
                .retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(RestClientResponseException.class, failure -> {
                    assertThat(failure.getStatusCode().value()).isEqualTo(409);
                    assertThat(failure.getResponseBodyAsString()).contains("TASK_NOT_FAILED");
                });

        transactionService.markProcessing(pending.getId(), clock.instant()).orElseThrow();
        transactionService.markPayloadFailed(pending.getId(), "TASK_PAYLOAD_INVALID", "invalid payload");
        assertThatThrownBy(() -> client.post().uri("/api/integration-tasks/{id}/retry", pending.getId())
                .retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(RestClientResponseException.class, failure -> {
                    assertThat(failure.getStatusCode().value()).isEqualTo(409);
                    assertThat(failure.getResponseBodyAsString()).contains("TASK_RETRY_NOT_ALLOWED");
                });

        employeeSyncService.synchronize(List.of(hr("E9039", "Conflict is not retryable", null, null, "ACTIVE")));
        IntegrationTask conflictTask = integrationTaskRepository.findAll().stream()
                .filter(task -> task.getEmployeeId().equals(employeeRepository.findByEmployeeNo("E9039")
                        .orElseThrow().getId())).findFirst().orElseThrow();
        transactionService.markProcessing(conflictTask.getId(), clock.instant()).orElseThrow();
        transactionService.markPayloadFailed(conflictTask.getId(), "IDEMPOTENCY_KEY_CONFLICT", "key conflict");
        assertThatThrownBy(() -> client.post().uri("/api/integration-tasks/{id}/retry", conflictTask.getId())
                .retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(RestClientResponseException.class, failure ->
                        assertThat(failure.getResponseBodyAsString()).contains("TASK_RETRY_NOT_ALLOWED"));
    }

    @Test
    void simultaneousManualRetryRequestsApproveOnlyOneAndDoNotCallGroupware() throws Exception {
        employeeSyncService.synchronize(List.of(hr("E9040", "Concurrent retry", null, null, "ACTIVE")));
        IntegrationTask task = integrationTaskRepository.findAll().getFirst();
        transactionService.markProcessing(task.getId(), clock.instant()).orElseThrow();
        transactionService.markPayloadFailed(task.getId(), "GROUPWARE_HTTP_ERROR", "safe error");
        CompletableFuture<Integer> first = retryStatus(task.getId());
        CompletableFuture<Integer> second = retryStatus(task.getId());

        assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(200, 409);
        assertThat(integrationTaskRepository.findById(task.getId()).orElseThrow().getStatus())
                .isEqualTo(IntegrationTaskStatus.PENDING);
        assertThat(GROUPWARE_STUB.requestsFor("E9040")).isEmpty();
    }

    @Test
    void retryApiDatabaseFailureRollsBackTheStateChange() {
        employeeSyncService.synchronize(List.of(hr("E9041", "Retry rollback", null, null, "ACTIVE")));
        IntegrationTask task = integrationTaskRepository.findAll().getFirst();
        transactionService.markProcessing(task.getId(), clock.instant()).orElseThrow();
        transactionService.markPayloadFailed(task.getId(), "GROUPWARE_HTTP_ERROR", "safe error");
        String constraintName = "ck_test_integration_task_reject_pending";
        jdbcTemplate.execute("alter table integration_task add constraint " + constraintName
                + " check (status <> 'PENDING')");
        try {
            assertThatThrownBy(() -> localClient().post()
                    .uri("/api/integration-tasks/{id}/retry", task.getId()).retrieve().toBodilessEntity())
                    .isInstanceOfSatisfying(RestClientResponseException.class, failure ->
                            assertThat(failure.getStatusCode().value()).isEqualTo(500));
            IntegrationTask stillFailed = integrationTaskRepository.findById(task.getId()).orElseThrow();
            assertThat(stillFailed.getStatus()).isEqualTo(IntegrationTaskStatus.FAILED);
            assertThat(stillFailed.getLastErrorCode()).isEqualTo("GROUPWARE_HTTP_ERROR");
        } finally {
            jdbcTemplate.execute("alter table integration_task drop constraint " + constraintName);
        }
    }

    @Test
    void groupwareClientClassifiesOnlyTheStableIdempotencyConflictCode() {
        GROUPWARE_STUB.forwardTo("http://localhost:" + port);
        java.util.UUID key = java.util.UUID.randomUUID();
        GroupwareAccountRequest original = new GroupwareAccountRequest("E9042", "Original", null, null, "ACTIVE");
        groupwareClient.send(IntegrationAction.CREATE_ACCOUNT, original, key);

        assertThatThrownBy(() -> groupwareClient.send(IntegrationAction.CREATE_ACCOUNT,
                new GroupwareAccountRequest("E9042", "Different", null, null, "ACTIVE"), key))
                .isInstanceOfSatisfying(GroupwareClientException.class, failure ->
                        assertThat(failure.getErrorCode()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT"));
    }

    @Test
    void idempotencyConflictFailsWithoutAutomaticRetryAndCannotBeRetriedManually() {
        employeeSyncService.synchronize(List.of(hr("E9043", "Expected payload", null, null, "ACTIVE")));
        IntegrationTask task = integrationTaskRepository.findAll().getFirst();
        GROUPWARE_STUB.forwardTo("http://localhost:" + port);
        localClient().post().uri("/mock/groupware/accounts")
                .header("Idempotency-Key", task.getIdempotencyKey().toString())
                .body(new GroupwareAccountRequest("E9043", "Different payload", null, null, "ACTIVE"))
                .retrieve().toBodilessEntity();

        assertThat(integrationWorker.runBatch()).isOne();
        IntegrationTask failed = integrationTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(IntegrationTaskStatus.FAILED);
        assertThat(failed.getLastErrorCode()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
        assertThat(failed.getRetryCount()).isZero();
        assertThat(integrationAttemptRepository.findAllByIntegrationTask_IdOrderByAttemptNoAsc(task.getId()))
                .singleElement().satisfies(attempt -> {
                    assertThat(attempt.getHttpStatus()).isEqualTo(409);
                    assertThat(attempt.getResult()).isEqualTo(IntegrationAttemptResult.FAILED);
                });
        assertThatThrownBy(() -> localClient().post()
                .uri("/api/integration-tasks/{id}/retry", task.getId()).retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(RestClientResponseException.class, failure -> {
                    assertThat(failure.getStatusCode().value()).isEqualTo(409);
                    assertThat(failure.getResponseBodyAsString()).contains("TASK_RETRY_NOT_ALLOWED");
                });
    }

    @Test
    void lostGroupwareResponseRetriesThroughRealMockAndReusesStoredResult() {
        employeeSyncService.synchronize(List.of(hr("E9006", "Response lost", null, null, "ACTIVE")));
        IntegrationTask task = integrationTaskRepository.findAll().getFirst();
        GROUPWARE_STUB.forwardTo("http://localhost:" + port);
        GROUPWARE_STUB.delayNextForwardedResponse();

        assertThat(integrationWorker.runBatch()).isOne();
        IntegrationTask waiting = integrationTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(waiting.getStatus()).isEqualTo(IntegrationTaskStatus.RETRY_WAIT);
        assertThat(waiting.getLastErrorCode()).isEqualTo("GROUPWARE_TIMEOUT");
        assertThat(integrationAttemptRepository.findAllByIntegrationTask_IdOrderByAttemptNoAsc(task.getId()))
                .singleElement().satisfies(attempt -> {
                    assertThat(attempt.getResult()).isEqualTo(IntegrationAttemptResult.FAILED);
                    assertThat(attempt.getAttemptNo()).isOne();
                });
        assertThat(accountStore.find("E9006")).satisfies(account -> assertThat(account.enabled()).isTrue());

        clock.advanceSeconds(5);
        clock.advanceMillis(1);
        assertThat(integrationWorker.runBatch()).isOne();

        IntegrationTask completed = integrationTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(IntegrationTaskStatus.SUCCESS);
        assertThat(GROUPWARE_STUB.requestsFor("E9006")).hasSize(2).containsOnly(
                new GroupwareAccountRequest("E9006", "Response lost", null, null, "ACTIVE"));
        assertThat(GROUPWARE_STUB.idempotencyKeysFor("E9006"))
                .containsExactly(task.getIdempotencyKey().toString(), task.getIdempotencyKey().toString());
        assertThat(integrationAttemptRepository.findAllByIntegrationTask_IdOrderByAttemptNoAsc(task.getId()))
                .extracting(IntegrationAttempt::getAttemptNo)
                .containsExactly(1, 2);
        assertThat(integrationAttemptRepository.findAllByIntegrationTask_IdOrderByAttemptNoAsc(task.getId()))
                .extracting(IntegrationAttempt::getResult)
                .containsExactly(IntegrationAttemptResult.FAILED, IntegrationAttemptResult.SUCCESS);
    }

    @Test
    void staleProcessingIsRecoveredAndFirstPersistedAttemptStartsAtOne() {
        employeeSyncService.synchronize(List.of(hr("E9007", "Stale", null, null, "ACTIVE")));
        IntegrationTask task = integrationTaskRepository.findAll().getFirst();
        Instant startedAt = clock.instant();
        transactionService.markProcessing(task.getId(), startedAt).orElseThrow();
        assertThat(java.time.Duration.between(startedAt,
                integrationTaskRepository.findById(task.getId()).orElseThrow().getProcessingStartedAt()).abs())
                .isLessThanOrEqualTo(java.time.Duration.ofNanos(1_000));

        clock.advanceSeconds(59);
        assertThat(integrationWorker.runBatch()).isZero();
        assertThat(integrationTaskRepository.findById(task.getId()).orElseThrow().getStatus())
                .isEqualTo(IntegrationTaskStatus.PROCESSING);

        clock.advanceSeconds(1);
        Instant recoveryTime = clock.instant();
        transactionService.recoverStaleProcessing(task.getId(), recoveryTime.minusSeconds(60), recoveryTime);
        IntegrationTask waiting = integrationTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(waiting.getStatus()).isEqualTo(IntegrationTaskStatus.RETRY_WAIT);
        assertThat(waiting.getRetryCount()).isZero();
        assertThat(waiting.getNextRetryAt()).isEqualTo(recoveryTime.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        assertThat(waiting.getProcessingStartedAt()).isNull();

        assertThat(integrationWorker.runBatch()).isOne();

        IntegrationTask completed = integrationTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(IntegrationTaskStatus.SUCCESS);
        assertThat(completed.getRetryCount()).isOne();
        assertThat(completed.getProcessingStartedAt()).isNull();
        assertThat(integrationAttemptRepository.findAllByIntegrationTask_IdOrderByAttemptNoAsc(task.getId()))
                .singleElement().satisfies(attempt -> assertThat(attempt.getAttemptNo()).isOne());
    }

    @Test
    void staleProcessingAtRetryLimitFailsWithoutCreatingAnAttempt() {
        employeeSyncService.synchronize(List.of(hr("E9008", "Exhausted stale", null, null, "ACTIVE")));
        IntegrationTask task = integrationTaskRepository.findAll().getFirst();
        jdbcTemplate.update("update integration_task set status = 'PROCESSING', retry_count = 3, "
                + "processing_started_at = ?, updated_at = ? where id = ?",
                java.sql.Timestamp.from(clock.instant()), java.sql.Timestamp.from(clock.instant()), task.getId());

        clock.advanceSeconds(61);
        assertThat(integrationWorker.runBatch()).isZero();

        IntegrationTask failed = integrationTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(IntegrationTaskStatus.FAILED);
        assertThat(failed.getLastErrorCode()).isEqualTo("PROCESSING_RECOVERY_EXHAUSTED");
        assertThat(failed.getProcessingStartedAt()).isNull();
        assertThat(integrationAttemptRepository.findAllByIntegrationTask_IdOrderByAttemptNoAsc(task.getId())).isEmpty();
        assertThat(GROUPWARE_STUB.requestsFor("E9008")).isEmpty();
    }

    @Test
    void recoveryUsesTheNextPersistedAttemptNumberInsteadOfRetryCount() {
        employeeSyncService.synchronize(List.of(hr("E9009", "Attempt sequence", null, null, "ACTIVE")));
        IntegrationTask task = integrationTaskRepository.findAll().getFirst();
        Instant firstAttemptAt = clock.instant();
        transactionService.markProcessing(task.getId(), firstAttemptAt).orElseThrow();
        transactionService.recordFailed(task.getId(), firstAttemptAt, firstAttemptAt, 503,
                "GROUPWARE_HTTP_ERROR", "Groupware returned an HTTP error", firstAttemptAt.plusSeconds(5));
        assertThat(integrationAttemptRepository.findAllByIntegrationTask_IdOrderByAttemptNoAsc(task.getId()))
                .extracting(IntegrationAttempt::getAttemptNo)
                .containsExactly(1);

        clock.advanceSeconds(5);
        clock.advanceMillis(1);
        Instant retryStartedAt = clock.instant();
        assertThat(transactionService.markProcessing(task.getId(), retryStartedAt)).isPresent();
        assertThat(integrationTaskRepository.findById(task.getId()).orElseThrow().getRetryCount()).isOne();

        clock.advanceSeconds(61);
        assertThat(integrationWorker.runBatch()).isOne();

        IntegrationTask completed = integrationTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(IntegrationTaskStatus.SUCCESS);
        assertThat(completed.getRetryCount()).isEqualTo(2);
        assertThat(integrationAttemptRepository.findAllByIntegrationTask_IdOrderByAttemptNoAsc(task.getId()))
                .extracting(IntegrationAttempt::getAttemptNo)
                .containsExactly(1, 2);
        assertThat(integrationAttemptRepository.findAllByIntegrationTask_IdOrderByAttemptNoAsc(task.getId()))
                .extracting(IntegrationAttempt::getResult)
                .containsExactly(IntegrationAttemptResult.FAILED, IntegrationAttemptResult.SUCCESS);
    }

    @Test
    void groupwareFailureDoesNotStopFollowingTaskAndStoresOnlySafeMessage() {
        GROUPWARE_STUB.failFor("E9010");
        employeeSyncService.synchronize(List.of(
                hr("E9010", "Secret employee", "first@company.com", "OPS01", "ACTIVE"),
                hr("E9011", "Next employee", "next@company.com", "OPS02", "ACTIVE")));

        assertThat(integrationWorker.runBatch()).isEqualTo(2);

        List<IntegrationTask> tasks = integrationTaskRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(IntegrationTask::getId))
                .toList();
        assertThat(tasks).extracting(IntegrationTask::getStatus)
                .containsExactly(IntegrationTaskStatus.RETRY_WAIT, IntegrationTaskStatus.SUCCESS);
        assertThat(tasks.getFirst().getLastErrorCode()).isEqualTo("GROUPWARE_HTTP_ERROR");
        assertThat(tasks.getFirst().getLastErrorMessage())
                .contains("HTTP error")
                .doesNotContain("internal Groupware details", "Secret employee", "first@company.com");
        assertThat(integrationAttemptRepository.findAllByIntegrationTask_IdOrderByAttemptNoAsc(tasks.getFirst().getId()))
                .singleElement()
                .satisfies(attempt -> {
                    assertThat(attempt.getResult()).isEqualTo(IntegrationAttemptResult.FAILED);
                    assertThat(attempt.getAttemptNo()).isOne();
                    assertThat(attempt.getHttpStatus()).isEqualTo(500);
                    assertThat(attempt.getErrorMessage())
                            .doesNotContain("internal Groupware details", "Secret employee", "first@company.com");
                });
        assertThat(GROUPWARE_STUB.find("E9011").enabled()).isTrue();
    }

    @Test
    void workerRejectsCallsFromAnExistingTransaction() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
                .execute(status -> integrationWorker.runBatch()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void disableEndpointUpdatesSnapshotAndMarksExistingAccountDisabled() {
        RestClient client = localClient();
        GroupwareAccountRequest request = new GroupwareAccountRequest(
                "E9012", "Former employee", null, "OPS01", "TERMINATED");
        client.put().uri("/mock/groupware/accounts/{employeeNo}", "E9012")
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .body(new GroupwareAccountRequest("E9012", "Former employee", null, "OPS01", "ACTIVE"))
                .retrieve().toBodilessEntity();

        client.patch().uri("/mock/groupware/accounts/{employeeNo}/disable", "E9012")
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .body(request).retrieve().toBodilessEntity();
        client.patch().uri("/mock/groupware/accounts/{employeeNo}/disable", "E9012")
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .body(new GroupwareAccountRequest("E9012", "Ignored while disabled", null, null, "TERMINATED"))
                .retrieve().toBodilessEntity();

        assertThat(accountStore.find("E9012")).satisfies(account -> {
            assertThat(account.enabled()).isFalse();
            assertThat(account.employmentStatus()).isEqualTo("TERMINATED");
            assertThat(account.name()).isEqualTo("Former employee");
        });

        assertThatThrownBy(() -> client.patch()
                .uri("/mock/groupware/accounts/{employeeNo}/disable", "E9015")
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .body(new GroupwareAccountRequest("E9015", "Missing account", null, null, "TERMINATED"))
                .retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(org.springframework.web.client.RestClientResponseException.class, failure -> {
                    assertThat(failure.getStatusCode().value()).isEqualTo(404);
                    assertThat(failure.getResponseBodyAsString()).contains("ACCOUNT_NOT_FOUND");
                });
        assertThat(accountStore.find("E9015")).isNull();
    }

    @Test
    void missingDisableAccountIsSuccessfulNoOpAndActiveEmployeeCanBeTerminatedAgain() {
        employeeSyncService.synchronize(List.of(
                hr("E9013", "Active employee", "active@company.com", "OPS01", "ACTIVE")));
        assertThat(integrationWorker.runBatch()).isEqualTo(1);
        assertThat(GROUPWARE_STUB.find("E9013").enabled()).isTrue();

        employeeSyncService.synchronize(List.of(
                hr("E9013", "Active employee", "active@company.com", "OPS01", "TERMINATED")));
        IntegrationTask termination = integrationTaskRepository.findAll().stream()
                .filter(task -> task.getAction() == IntegrationAction.DISABLE_ACCOUNT)
                .findFirst().orElseThrow();
        assertThat(integrationWorker.runBatch()).isEqualTo(1);
        assertThat(integrationTaskRepository.findById(termination.getId()).orElseThrow().getStatus())
                .isEqualTo(IntegrationTaskStatus.SUCCESS);
        assertThat(GROUPWARE_STUB.find("E9013").enabled()).isFalse();

        employeeSyncService.synchronize(List.of(
                hr("E9013", "Changed after termination", "active@company.com", "OPS01", "TERMINATED")));
        IntegrationTask repeatedTermination = integrationTaskRepository.findAll().stream()
                .filter(task -> task.getId() > termination.getId())
                .findFirst().orElseThrow();
        assertThat(repeatedTermination.getAction()).isEqualTo(IntegrationAction.DISABLE_ACCOUNT);
        assertThat(integrationWorker.runBatch()).isEqualTo(1);
        assertThat(integrationTaskRepository.findById(repeatedTermination.getId()).orElseThrow().getStatus())
                .isEqualTo(IntegrationTaskStatus.SUCCESS);
        assertThat(GROUPWARE_STUB.find("E9013").enabled()).isFalse();

        employeeSyncService.synchronize(List.of(
                hr("E9014", "Never active", null, null, "TERMINATED")));
        IntegrationTask missingAccountDisable = integrationTaskRepository.findAll().stream()
                .filter(task -> task.getAction() == IntegrationAction.DISABLE_ACCOUNT
                        && task.getEmployeeId().equals(employeeRepository.findByEmployeeNo("E9014")
                        .orElseThrow().getId()))
                .findFirst().orElseThrow();
        assertThat(integrationWorker.runBatch()).isEqualTo(1);
        assertThat(integrationTaskRepository.findById(missingAccountDisable.getId()).orElseThrow().getStatus())
                .isEqualTo(IntegrationTaskStatus.SUCCESS);
        assertThat(GROUPWARE_STUB.find("E9014")).isNull();
        IntegrationAttempt disableAttempt = integrationAttemptRepository
                .findAllByIntegrationTask_IdOrderByAttemptNoAsc(missingAccountDisable.getId()).getFirst();
        assertThat(disableAttempt.getResult()).isEqualTo(IntegrationAttemptResult.SUCCESS);
        assertThat(disableAttempt.getHttpStatus()).isEqualTo(404);
    }

    @Test
    void retriesUseAttemptNumberAndFiveFifteenThirtySecondBackoffThenFailAfterFourthCall() {
        employeeSyncService.synchronize(List.of(hr("E9020", "Retry", "retry@company.com", "OPS01", "ACTIVE")));
        IntegrationTask task = integrationTaskRepository.findAll().getFirst();
        Instant firstFailureTime = clock.instant();
        GROUPWARE_STUB.failFor("E9020");

        assertThat(integrationWorker.runBatch()).isOne();
        IntegrationTask first = integrationTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(first.getStatus()).isEqualTo(IntegrationTaskStatus.RETRY_WAIT);
        assertThat(first.getRetryCount()).isZero();
        assertInstantClose(first.getNextRetryAt(), firstFailureTime.plusSeconds(5));
        employeeSyncService.synchronize(List.of(hr("E9026", "Ready task", null, null, "ACTIVE")));
        assertThat(integrationWorker.runBatch()).isOne();
        IntegrationTask readyTask = integrationTaskRepository.findAll().stream()
                .filter(candidate -> candidate.getEmployeeId().equals(employeeRepository.findByEmployeeNo("E9026")
                        .orElseThrow().getId()))
                .findFirst().orElseThrow();
        assertThat(integrationTaskRepository.findById(readyTask.getId()).orElseThrow().getStatus())
                .isEqualTo(IntegrationTaskStatus.SUCCESS);
        assertThat(integrationTaskRepository.findById(task.getId()).orElseThrow().getStatus())
                .isEqualTo(IntegrationTaskStatus.RETRY_WAIT);

        clock.advanceSeconds(5);
        clock.advanceMillis(1);
        Instant secondFailureTime = clock.instant();
        assertThat(integrationWorker.runBatch()).isOne();
        IntegrationTask second = integrationTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(second.getStatus()).isEqualTo(IntegrationTaskStatus.RETRY_WAIT);
        assertThat(second.getRetryCount()).isEqualTo(1);
        assertInstantClose(second.getNextRetryAt(), secondFailureTime.plusSeconds(15));

        clock.advanceSeconds(15);
        clock.advanceMillis(1);
        Instant thirdFailureTime = clock.instant();
        assertThat(integrationWorker.runBatch()).isOne();
        IntegrationTask third = integrationTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(third.getStatus()).isEqualTo(IntegrationTaskStatus.RETRY_WAIT);
        assertThat(third.getRetryCount()).isEqualTo(2);
        assertInstantClose(third.getNextRetryAt(), thirdFailureTime.plusSeconds(30));

        clock.advanceSeconds(30);
        clock.advanceMillis(1);
        assertThat(integrationWorker.runBatch()).isOne();
        IntegrationTask exhausted = integrationTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(exhausted.getStatus()).isEqualTo(IntegrationTaskStatus.FAILED);
        assertThat(exhausted.getRetryCount()).isEqualTo(3);
        assertThat(exhausted.getNextRetryAt()).isNull();
        assertThat(GROUPWARE_STUB.idempotencyKeysFor("E9020"))
                .containsExactly(task.getIdempotencyKey().toString(), task.getIdempotencyKey().toString(),
                        task.getIdempotencyKey().toString(), task.getIdempotencyKey().toString());
        assertThat(GROUPWARE_STUB.requestsFor("E9020"))
                .containsOnly(new GroupwareAccountRequest("E9020", "Retry", "retry@company.com", "OPS01", "ACTIVE"));
        assertThat(integrationAttemptRepository.findAllByIntegrationTask_IdOrderByAttemptNoAsc(task.getId()))
                .extracting(IntegrationAttempt::getAttemptNo)
                .containsExactly(1, 2, 3, 4);
    }

    @Test
    void nonRetryableHttpErrorsAndUnrelatedDisable404FailImmediatelyWithAttempt() {
        employeeSyncService.synchronize(List.of(
                hr("E9021", "Bad request", null, null, "ACTIVE"),
                hr("E9022", "Wrong 404", null, null, "TERMINATED")));
        List<IntegrationTask> tasks = integrationTaskRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(IntegrationTask::getId)).toList();
        GROUPWARE_STUB.failWithStatus("E9021", 400);
        GROUPWARE_STUB.returnUnrelatedNotFoundFor("E9022");

        assertThat(integrationWorker.runBatch()).isEqualTo(2);
        assertThat(tasks).allSatisfy(task -> {
            IntegrationTask failed = integrationTaskRepository.findById(task.getId()).orElseThrow();
            assertThat(failed.getStatus()).isEqualTo(IntegrationTaskStatus.FAILED);
            assertThat(failed.getRetryCount()).isZero();
            assertThat(integrationAttemptRepository.findAllByIntegrationTask_IdOrderByAttemptNoAsc(task.getId()))
                    .singleElement().satisfies(attempt -> {
                        assertThat(attempt.getResult()).isEqualTo(IntegrationAttemptResult.FAILED);
                        assertThat(attempt.getHttpStatus()).isIn(400, 404);
                    });
        });
    }

    @Test
    void invalidPayloadFailsWithoutCallingGroupwareOrCreatingAttempt() {
        employeeSyncService.synchronize(List.of(hr("E9023", "Invalid payload", null, null, "ACTIVE")));
        IntegrationTask task = integrationTaskRepository.findAll().getFirst();
        jdbcTemplate.update("update integration_task set payload = ? where id = ?", "invalid json", task.getId());

        assertThat(integrationWorker.runBatch()).isOne();
        IntegrationTask failed = integrationTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(IntegrationTaskStatus.FAILED);
        assertThat(failed.getLastErrorCode()).isEqualTo("TASK_PAYLOAD_INVALID");
        assertThat(integrationAttemptRepository.findAllByIntegrationTask_IdOrderByAttemptNoAsc(task.getId())).isEmpty();
        assertThat(GROUPWARE_STUB.find("E9023")).isNull();
    }

    @Test
    void processingIsCommittedBeforeBlockedHttpAndHttpCallRunsWithoutTransaction() throws Exception {
        employeeSyncService.synchronize(List.of(hr("E9024", "Blocking", null, null, "ACTIVE")));
        IntegrationTask task = integrationTaskRepository.findAll().getFirst();
        GROUPWARE_STUB.blockNextRequest();
        CompletableFuture<Integer> run = CompletableFuture.supplyAsync(integrationWorker::runBatch);
        try {
            assertThat(GROUPWARE_STUB.awaitBlockedRequest()).isTrue();
            assertThat(integrationTaskRepository.findById(task.getId()).orElseThrow().getStatus())
                    .isEqualTo(IntegrationTaskStatus.PROCESSING);
        } finally {
            GROUPWARE_STUB.releaseBlockedRequest();
        }
        assertThat(run.get(10, TimeUnit.SECONDS)).isOne();
        assertThat(integrationTaskRepository.findById(task.getId()).orElseThrow().getStatus())
                .isEqualTo(IntegrationTaskStatus.SUCCESS);
    }

    @Test
    void taskResultRollsBackWhenAttemptInsertViolatesDatabaseCheckConstraint() {
        employeeSyncService.synchronize(List.of(hr("E9025", "Atomic", null, null, "ACTIVE")));
        IntegrationTask task = integrationTaskRepository.findAll().getFirst();
        transactionService.markProcessing(task.getId(), clock.instant()).orElseThrow();
        Instant instant = clock.instant();
        String constraintName = "ck_test_integration_attempt_reject_success";
        jdbcTemplate.execute("alter table integration_attempt add constraint " + constraintName
                + " check (result <> 'SUCCESS')");

        try {
            assertThatThrownBy(() -> transactionService.recordSucceeded(task.getId(), instant, instant, 201))
                    .isInstanceOf(DataIntegrityViolationException.class);

            assertThat(integrationTaskRepository.findById(task.getId()).orElseThrow().getStatus())
                    .isEqualTo(IntegrationTaskStatus.PROCESSING);
            assertThat(integrationAttemptRepository.findAllByIntegrationTask_IdOrderByAttemptNoAsc(task.getId()))
                    .isEmpty();
        } finally {
            jdbcTemplate.execute("alter table integration_attempt drop constraint " + constraintName);
        }
    }

    @Test
    void attemptInsertRollsBackWhenTaskUpdateFailsDatabaseCheckConstraint() {
        employeeSyncService.synchronize(List.of(hr("E9027", "Atomic update", null, null, "ACTIVE")));
        IntegrationTask task = integrationTaskRepository.findAll().getFirst();
        transactionService.markProcessing(task.getId(), clock.instant()).orElseThrow();
        String constraintName = "ck_test_integration_task_reject_success";
        jdbcTemplate.execute("alter table integration_task add constraint " + constraintName
                + " check (status <> 'SUCCESS')");
        Instant instant = clock.instant();

        try {
            assertThatThrownBy(() -> transactionService.recordSucceeded(
                    task.getId(), instant, instant, 201))
                    .isInstanceOf(DataIntegrityViolationException.class);

            assertThat(integrationTaskRepository.findById(task.getId()).orElseThrow().getStatus())
                    .isEqualTo(IntegrationTaskStatus.PROCESSING);
            assertThat(integrationAttemptRepository.findAllByIntegrationTask_IdOrderByAttemptNoAsc(task.getId()))
                    .isEmpty();
        } finally {
            jdbcTemplate.execute("alter table integration_task drop constraint " + constraintName);
        }
    }

    private RestClient localClient() {
        return AdminHttpSession.login("http://localhost:" + port).client();
    }

    private CompletableFuture<Integer> retryStatus(Long taskId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return localClient().post().uri("/api/integration-tasks/{id}/retry", taskId)
                        .retrieve().toBodilessEntity().getStatusCode().value();
            } catch (RestClientResponseException expectedConflict) {
                return expectedConflict.getStatusCode().value();
            }
        });
    }

    private static void assertInstantClose(Instant actual, Instant expected) {
        assertThat(java.time.Duration.between(expected, actual).abs())
                .isLessThanOrEqualTo(java.time.Duration.ofMillis(1));
    }

    private void clearDatabase() {
        integrationAttemptRepository.deleteAllInBatch();
        integrationTaskRepository.deleteAllInBatch();
        auditLogRepository.deleteAllInBatch();
        syncItemRepository.deleteAllInBatch();
        syncJobRepository.deleteAllInBatch();
        employeeRepository.deleteAllInBatch();
    }

    private static HrEmployeeResponse hr(
            String employeeNo,
            String employeeName,
            String email,
            String departmentCode,
            String employmentStatus
    ) {
        return new HrEmployeeResponse(employeeNo, employeeName, email, departmentCode, employmentStatus);
    }
}
