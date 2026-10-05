package com.kimsooin77.sync.integration;

import com.kimsooin77.sync.audit.AuditLogRepository;
import com.kimsooin77.sync.employee.EmployeeRepository;
import com.kimsooin77.sync.employee.EmploymentStatus;
import com.kimsooin77.sync.employee.PostgreSqlTestConfiguration;
import com.kimsooin77.sync.sync.EmployeeSyncService;
import com.kimsooin77.sync.sync.HrEmployeeResponse;
import com.kimsooin77.sync.sync.SyncItemRepository;
import com.kimsooin77.sync.sync.SyncJobRepository;
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
                "app.mock.groupware.enabled=true"
        })
@Import({PostgreSqlTestConfiguration.class, IntegrationWorkerTestConfiguration.class})
class IntegrationWorkerIntegrationTest {

    private static final GroupwareStubServer GROUPWARE_STUB = GroupwareStubServer.start();

    @DynamicPropertySource
    static void groupwareBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("app.groupware.base-url", GROUPWARE_STUB::baseUrl);
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

        client.post().uri("/mock/groupware/accounts").body(request).retrieve().toBodilessEntity();
        assertThatThrownBy(() -> client.post().uri("/mock/groupware/accounts")
                .body(request).retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(RestClientResponseException.class, failure ->
                        assertThat(failure.getStatusCode().value()).isEqualTo(409));

        client.put().uri("/mock/groupware/accounts/{employeeNo}", "E9002")
                .body(new GroupwareAccountRequest("E9002", "First", null, "OPS01", "ACTIVE"))
                .retrieve().toBodilessEntity();

        client.put().uri("/mock/groupware/accounts/{employeeNo}", "E9003")
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
                .body(new GroupwareAccountRequest("E9012", "Former employee", null, "OPS01", "ACTIVE"))
                .retrieve().toBodilessEntity();

        client.patch().uri("/mock/groupware/accounts/{employeeNo}/disable", "E9012")
                .body(request).retrieve().toBodilessEntity();
        client.patch().uri("/mock/groupware/accounts/{employeeNo}/disable", "E9012")
                .body(new GroupwareAccountRequest("E9012", "Ignored while disabled", null, null, "TERMINATED"))
                .retrieve().toBodilessEntity();

        assertThat(accountStore.find("E9012")).satisfies(account -> {
            assertThat(account.enabled()).isFalse();
            assertThat(account.employmentStatus()).isEqualTo("TERMINATED");
            assertThat(account.name()).isEqualTo("Former employee");
        });

        assertThatThrownBy(() -> client.patch()
                .uri("/mock/groupware/accounts/{employeeNo}/disable", "E9015")
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
    void attemptAndTaskResultRollbackTogetherWhenAttemptInsertViolatesUniqueConstraint() {
        employeeSyncService.synchronize(List.of(hr("E9025", "Atomic", null, null, "ACTIVE")));
        IntegrationTask task = integrationTaskRepository.findAll().getFirst();
        var command = transactionService.markProcessing(task.getId(), clock.instant()).orElseThrow();
        Instant instant = clock.instant();
        integrationAttemptRepository.saveAndFlush(IntegrationAttempt.failed(
                task, command.retryCount() + 1, instant, instant, 503, "GROUPWARE_HTTP_ERROR", "safe failure"));

        assertThatThrownBy(() -> transactionService.recordSucceeded(
                task.getId(), command.retryCount() + 1, instant, instant, 201))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(integrationTaskRepository.findById(task.getId()).orElseThrow().getStatus())
                .isEqualTo(IntegrationTaskStatus.PROCESSING);
        assertThat(integrationAttemptRepository.findAllByIntegrationTask_IdOrderByAttemptNoAsc(task.getId()))
                .singleElement()
                .satisfies(attempt -> assertThat(attempt.getResult()).isEqualTo(IntegrationAttemptResult.FAILED));
    }

    @Test
    void attemptInsertRollsBackWhenTaskUpdateFailsDatabaseCheckConstraint() {
        employeeSyncService.synchronize(List.of(hr("E9027", "Atomic update", null, null, "ACTIVE")));
        IntegrationTask task = integrationTaskRepository.findAll().getFirst();
        var command = transactionService.markProcessing(task.getId(), clock.instant()).orElseThrow();
        String constraintName = "ck_test_integration_task_reject_success";
        jdbcTemplate.execute("alter table integration_task add constraint " + constraintName
                + " check (status <> 'SUCCESS')");
        Instant instant = clock.instant();

        try {
            assertThatThrownBy(() -> transactionService.recordSucceeded(
                    task.getId(), command.retryCount() + 1, instant, instant, 201))
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
        return RestClient.builder().baseUrl("http://localhost:" + port).build();
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
