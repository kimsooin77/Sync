package com.kimsooin77.sync.simulation.hr;

import com.kimsooin77.sync.AdminHttpSession;
import com.kimsooin77.sync.audit.AuditLogRepository;
import com.kimsooin77.sync.employee.EmployeeRepository;
import com.kimsooin77.sync.employee.PostgreSqlTestConfiguration;
import com.kimsooin77.sync.integration.GroupwareAccount;
import com.kimsooin77.sync.integration.GroupwareStubServer;
import com.kimsooin77.sync.integration.IntegrationAction;
import com.kimsooin77.sync.integration.IntegrationAttemptRepository;
import com.kimsooin77.sync.integration.IntegrationTask;
import com.kimsooin77.sync.integration.IntegrationTaskRepository;
import com.kimsooin77.sync.integration.IntegrationTaskStatus;
import com.kimsooin77.sync.integration.IntegrationWorker;
import com.kimsooin77.sync.simulation.groupware.MockGroupwareAccountStore;
import com.kimsooin77.sync.sync.EmployeeSyncService;
import com.kimsooin77.sync.sync.HrEmployeeResponse;
import com.kimsooin77.sync.sync.SyncItemRepository;
import com.kimsooin77.sync.sync.SyncJobRepository;
import com.kimsooin77.sync.sync.SyncJobResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.mock.hr.enabled=true", "app.mock.hr.scenario=initial", "app.mock.groupware.enabled=true"})
@Import(PostgreSqlTestConfiguration.class)
class MockHrScenarioFlowIntegrationTest {
    private static final GroupwareStubServer GROUPWARE_PROXY = GroupwareStubServer.start();
    @LocalServerPort int port;
    @Autowired MockHrDataset dataset;
    @Autowired MockHrScenarioState scenarioState;
    @Autowired EmployeeSyncService employeeSyncService;
    @Autowired IntegrationWorker worker;
    @Autowired MockGroupwareAccountStore groupware;
    @Autowired IntegrationTaskRepository tasks;
    @Autowired IntegrationAttemptRepository attempts;
    @Autowired AuditLogRepository audits;
    @Autowired SyncItemRepository items;
    @Autowired SyncJobRepository jobs;
    @Autowired EmployeeRepository employees;

    @DynamicPropertySource
    static void groupwareBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("app.groupware.base-url", GROUPWARE_PROXY::baseUrl);
    }

    @BeforeEach void resetState() {
        attempts.deleteAllInBatch(); tasks.deleteAllInBatch(); audits.deleteAllInBatch();
        items.deleteAllInBatch(); jobs.deleteAllInBatch(); employees.deleteAllInBatch();
        groupware.clear(); GROUPWARE_PROXY.clear();
        GROUPWARE_PROXY.forwardTo("http://localhost:" + port);
        scenarioState.changeTo("initial");
    }

    @AfterAll static void stopGroupwareProxy() { GROUPWARE_PROXY.close(); }

    @Test void runtimeScenarioChangeUpdatesAndDisablesAccountsWithoutRestart() {
        AdminHttpSession admin = AdminHttpSession.login("http://localhost:" + port);
        assertThat(admin.client().get().uri("/mock/hr/scenario").retrieve().body(String.class))
                .contains("\"scenario\":\"initial\"");

        SyncJobResult initial = synchronizeCurrentScenario();
        assertThat(initial.insertedCount()).isEqualTo(3);
        assertThat(worker.runBatch()).isEqualTo(3);
        assertThat(tasks.findAll()).allSatisfy(task -> assertThat(task.getStatus()).isEqualTo(IntegrationTaskStatus.SUCCESS));
        assertThat(groupware.find("E1002")).isNotNull().extracting(GroupwareAccount::enabled).isEqualTo(true);
        assertThat(groupware.find("E1003")).isNotNull().extracting(GroupwareAccount::enabled).isEqualTo(true);

        admin.client().put().uri("/mock/hr/scenario").body(Map.of("scenario", "changed"))
                .retrieve().toBodilessEntity();
        assertThat(admin.client().get().uri("/mock/hr/scenario").retrieve().body(String.class))
                .contains("\"scenario\":\"changed\"");

        SyncJobResult changed = synchronizeCurrentScenario();
        assertThat(changed.insertedCount()).isZero();
        assertThat(changed.updatedCount()).isEqualTo(2);
        assertThat(changed.skippedCount()).isEqualTo(1);

        Map<String, IntegrationTask> changedTasks = tasks.findAll().stream()
                .filter(task -> task.getAction() == IntegrationAction.UPDATE_ACCOUNT
                        || task.getAction() == IntegrationAction.DISABLE_ACCOUNT)
                .collect(Collectors.toMap(this::employeeNumber, Function.identity()));
        assertThat(changedTasks).containsKeys("E1002", "E1003");
        assertThat(changedTasks.get("E1002").getAction()).isEqualTo(IntegrationAction.UPDATE_ACCOUNT);
        assertThat(changedTasks.get("E1003").getAction()).isEqualTo(IntegrationAction.DISABLE_ACCOUNT);
        assertThat(worker.runBatch()).isEqualTo(2);
        assertThat(groupware.find("E1002")).satisfies(account -> {
            assertThat(account.enabled()).isTrue();
            assertThat(account.departmentCode()).isEqualTo("DEV02");
        });
        assertThat(groupware.find("E1003")).satisfies(account -> {
            assertThat(account.enabled()).isFalse();
            assertThat(account.employmentStatus()).isEqualTo("TERMINATED");
        });

        SyncJobResult repeated = synchronizeCurrentScenario();
        assertThat(repeated.insertedCount()).isZero();
        assertThat(repeated.updatedCount()).isZero();
        assertThat(repeated.skippedCount()).isEqualTo(3);
        assertThat(tasks.count()).isEqualTo(5);
    }

    private SyncJobResult synchronizeCurrentScenario() {
        List<HrEmployeeResponse> rows = dataset.employees().stream()
                .map(employee -> new HrEmployeeResponse(employee.employeeNo(), employee.employeeName(), employee.email(),
                        employee.departmentCode(), employee.employmentStatus()))
                .toList();
        return employeeSyncService.synchronize(rows);
    }

    private String employeeNumber(IntegrationTask task) {
        return employees.findById(task.getEmployeeId()).orElseThrow().getEmployeeNo();
    }
}
