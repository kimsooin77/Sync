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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.integration.worker.enabled=false",
                "app.mock.groupware.enabled=true"
        })
@Import(PostgreSqlTestConfiguration.class)
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
    private PlatformTransactionManager transactionManager;

    @LocalServerPort
    private int port;

    @BeforeEach
    void clearState() {
        clearDatabase();
        accountStore.clear();
        GROUPWARE_STUB.clear();
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
                .containsExactly(IntegrationTaskStatus.FAILED, IntegrationTaskStatus.SUCCESS);
        assertThat(tasks.getFirst().getLastErrorCode()).isEqualTo("GROUPWARE_HTTP_ERROR");
        assertThat(tasks.getFirst().getLastErrorMessage())
                .contains("HTTP error")
                .doesNotContain("internal Groupware details", "Secret employee", "first@company.com");
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

        client.patch().uri("/mock/groupware/accounts/{employeeNo}/disable", "E9015")
                .body(new GroupwareAccountRequest("E9015", "Missing account", null, null, "TERMINATED"))
                .retrieve().toBodilessEntity();
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
    }

    private RestClient localClient() {
        return RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    private void clearDatabase() {
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
