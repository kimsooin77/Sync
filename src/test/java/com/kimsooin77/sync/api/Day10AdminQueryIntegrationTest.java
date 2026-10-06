package com.kimsooin77.sync.api;
import com.kimsooin77.sync.AdminHttpSession;
import com.kimsooin77.sync.audit.AuditLogRepository;
import com.kimsooin77.sync.employee.EmployeeRepository;
import com.kimsooin77.sync.employee.EmploymentStatus;
import com.kimsooin77.sync.employee.PostgreSqlTestConfiguration;
import com.kimsooin77.sync.integration.IntegrationAttemptRepository;
import com.kimsooin77.sync.integration.IntegrationAttempt;
import com.kimsooin77.sync.integration.IntegrationTaskRepository;
import com.kimsooin77.sync.sync.EmployeeSyncService;
import com.kimsooin77.sync.sync.HrEmployeeResponse;
import com.kimsooin77.sync.sync.SyncItemRepository;
import com.kimsooin77.sync.sync.SyncJob;
import com.kimsooin77.sync.sync.SyncJobRepository;
import com.kimsooin77.sync.sync.SyncJobTransactionService;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import(PostgreSqlTestConfiguration.class)
class Day10AdminQueryIntegrationTest {
    @LocalServerPort int port;
    @Autowired EmployeeSyncService sync;
    @Autowired EmployeeRepository employees;
    @Autowired SyncJobRepository jobs;
    @Autowired SyncJobTransactionService jobTransactions;
    @Autowired SyncItemRepository items;
    @Autowired AuditLogRepository audits;
    @Autowired IntegrationTaskRepository tasks;
    @Autowired IntegrationAttemptRepository attempts;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory entityManagerFactory;

    @BeforeEach void clear() {
        attempts.deleteAllInBatch(); tasks.deleteAllInBatch(); audits.deleteAllInBatch();
        items.deleteAllInBatch(); jobs.deleteAllInBatch(); employees.deleteAllInBatch();
        sync.synchronize(List.of(hr("E2001", "Kim One", "one@example.com", "DEV", "ACTIVE"),
                hr("E2002", "Lee Two", null, "OPS", "ON_LEAVE")));
        jobs.saveAndFlush(new SyncJob(0));
    }

    @Test void employeeSearchDetailPaginationAndInvalidParametersAreHandled() {
        RestClient client = admin();
        String body = client.get().uri("/api/employees?keyword=kim&employmentStatus=ACTIVE&page=0&size=1")
                .retrieve().body(String.class);
        assertThat(body).contains("E2001", "totalElements", "totalPages").doesNotContain("E2002");
        assertThat(client.get().uri("/api/employees?page=99&size=1").retrieve().body(String.class))
                .contains("\"content\":[]", "\"totalElements\":2");
        assertThatThrownBy(() -> client.get().uri("/api/employees?page=-1").retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(RestClientResponseException.class, failure -> {
                    assertThat(failure.getStatusCode().value()).isEqualTo(400);
                    assertThat(failure.getResponseBodyAsString()).contains("INVALID_REQUEST");
                });
        assertBadRequest(() -> client.get().uri("/api/employees?employmentStatus=UNKNOWN").retrieve().toBodilessEntity());
        assertBadRequest(() -> client.get().uri("/api/employees?size=101").retrieve().toBodilessEntity());
        long employeeId = employees.findByEmployeeNo("E2001").orElseThrow().getId();
        assertThat(client.get().uri("/api/employees/{id}", employeeId).retrieve().body(String.class))
                .contains("E2001", "Kim One");
        assertNotFound(() -> client.get().uri("/api/employees/{id}", Long.MAX_VALUE).retrieve().toBodilessEntity(),
                "EMPLOYEE_NOT_FOUND");
    }

    @Test void taskFiltersProjectionDetailsAttemptsAndMalformedPayloadAreHandled() {
        RestClient client = admin();
        var task = tasks.findAll().getFirst();
        long taskId = task.getId();
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        String list = client.get().uri("/api/integration-tasks").retrieve().body(String.class);
        assertThat(list).contains("E2001", "E2002", "CREATE_ACCOUNT").doesNotContain("payload");
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(2);
        assertThat(client.get().uri("/api/integration-tasks?status=PENDING&action=CREATE_ACCOUNT&employeeNo=E2001")
                .retrieve().body(String.class)).contains("E2001", "CREATE_ACCOUNT", "\"totalElements\":1");
        assertThat(client.get().uri("/api/integration-tasks?employeeNo=NO-SUCH-EMPLOYEE")
                .retrieve().body(String.class)).contains("\"content\":[]", "\"totalElements\":0");
        assertBadRequest(() -> client.get().uri("/api/integration-tasks?status=UNKNOWN").retrieve().toBodilessEntity());
        assertBadRequest(() -> client.get().uri("/api/integration-tasks?action=UNKNOWN").retrieve().toBodilessEntity());
        assertBadRequest(() -> client.get().uri("/api/integration-tasks?size=101").retrieve().toBodilessEntity());
        assertNotFound(() -> client.get().uri("/api/integration-tasks/{id}", Long.MAX_VALUE)
                .retrieve().toBodilessEntity(), "TASK_NOT_FOUND");

        jdbc.update("update integration_task set payload = ? where id = ?", "person@example.com secret broken", taskId);
        String detail = client.get().uri("/api/integration-tasks/{id}", taskId).retrieve().body(String.class);
        assertThat(detail).contains("\"payload\":null", "\"payloadParseError\":true")
                .doesNotContain("person@example.com", "secret broken");
        Instant now = Instant.now();
        attempts.saveAndFlush(IntegrationAttempt.succeeded(task, 2, now, now, 201));
        attempts.saveAndFlush(IntegrationAttempt.succeeded(task, 1, now, now, 201));
        String firstAttemptPage = client.get().uri("/api/integration-tasks/{id}/attempts?page=0&size=1", taskId)
                .retrieve().body(String.class);
        assertThat(firstAttemptPage).contains("\"attemptNo\":1", "\"result\":\"SUCCESS\"", "\"totalElements\":2")
                .doesNotContain("\"attemptNo\":2");
        assertThat(client.get().uri("/api/integration-tasks/{id}/attempts?page=1&size=1", taskId)
                .retrieve().body(String.class)).contains("\"attemptNo\":2");
        long noAttemptTaskId = tasks.findAll().stream().filter(candidate -> !candidate.getId().equals(taskId))
                .findFirst().orElseThrow().getId();
        assertThat(client.get().uri("/api/integration-tasks/{id}/attempts", noAttemptTaskId)
                .retrieve().body(String.class)).contains("\"content\":[]", "\"totalElements\":0");
        assertNotFound(() -> client.get().uri("/api/integration-tasks/{id}/attempts", Long.MAX_VALUE)
                .retrieve().toBodilessEntity(), "TASK_NOT_FOUND");
    }

    @Test void auditHistoryPaginatesAndSyncJobListFiltersSortsAndUsesSyncJobId() {
        RestClient client = admin();
        long employeeId = employees.findByEmployeeNo("E2001").orElseThrow().getId();
        sync.synchronize(List.of(hr("E2001", "Kim One Updated", "one@example.com", "DEV", "ACTIVE")));
        sync.synchronize(List.of(hr("E2001", "Kim One Updated Again", "one@example.com", "DEV", "ACTIVE")));
        long auditId = audits.findAllByEmployee_IdOrderByIdAsc(employeeId).getFirst().getId();
        jdbc.update("update audit_log set changes = ? where id = ?", "private malformed JSON", auditId);
        String history = client.get().uri("/api/employees/{id}/audit-logs", employeeId).retrieve().body(String.class);
        assertThat(history).contains("\"changes\":null", "\"changesParseError\":true")
                .doesNotContain("private malformed JSON");
        String auditPage = client.get().uri("/api/employees/{id}/audit-logs?page=0&size=1", employeeId)
                .retrieve().body(String.class);
        assertThat(auditPage).contains("\"totalElements\":3", "\"totalPages\":3");
        Long newestAuditId = audits.findAllByEmployee_IdOrderByIdAsc(employeeId).getLast().getId();
        assertThat(auditPage).contains("\"id\":" + newestAuditId)
                .doesNotContain("\"id\":" + audits.findAllByEmployee_IdOrderByIdAsc(employeeId).getFirst().getId());
        assertNotFound(() -> client.get().uri("/api/employees/{id}/audit-logs", Long.MAX_VALUE)
                .retrieve().toBodilessEntity(), "EMPLOYEE_NOT_FOUND");

        Long firstRunningId = jobTransactions.start(0);
        Long newestRunningId = jobTransactions.start(0);
        Long completedId = jobTransactions.start(0);
        jobTransactions.complete(completedId);
        String completedList = client.get().uri("/api/sync-jobs?status=COMPLETED").retrieve().body(String.class);
        assertThat(completedList).contains("\"syncJobId\":" + completedId, "COMPLETED")
                .doesNotContain("failureMessage");
        String runningList = client.get().uri("/api/sync-jobs?status=RUNNING&page=0&size=10")
                .retrieve().body(String.class);
        assertThat(runningList).contains("\"syncJobId\":" + newestRunningId, "\"syncJobId\":" + firstRunningId);
        assertThat(runningList.indexOf("\"syncJobId\":" + newestRunningId))
                .isLessThan(runningList.indexOf("\"syncJobId\":" + firstRunningId));
        assertThat(client.get().uri("/api/sync-jobs?status=FAILED").retrieve().body(String.class))
                .contains("\"content\":[]", "\"totalElements\":0");
        assertBadRequest(() -> client.get().uri("/api/sync-jobs?status=UNKNOWN").retrieve().toBodilessEntity());
    }

    private static void assertBadRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable request) {
        assertThatThrownBy(request).isInstanceOfSatisfying(RestClientResponseException.class, failure ->
                assertThat(failure.getStatusCode().value()).isEqualTo(400));
    }

    private static void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable request, String errorCode) {
        assertThatThrownBy(request).isInstanceOfSatisfying(RestClientResponseException.class, failure -> {
            assertThat(failure.getStatusCode().value()).isEqualTo(404);
            assertThat(failure.getResponseBodyAsString()).contains(errorCode);
        });
    }

    private RestClient admin() { return AdminHttpSession.login("http://localhost:" + port).client(); }
    private static HrEmployeeResponse hr(String no, String name, String email, String dept, String status) {
        return new HrEmployeeResponse(no, name, email, dept, status);
    }
}
