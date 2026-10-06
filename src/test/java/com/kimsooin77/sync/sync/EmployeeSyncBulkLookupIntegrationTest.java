package com.kimsooin77.sync.sync;

import com.kimsooin77.sync.employee.Employee;
import com.kimsooin77.sync.employee.EmployeeRepository;
import com.kimsooin77.sync.employee.EmploymentStatus;
import com.kimsooin77.sync.employee.PostgreSqlTestConfiguration;
import com.kimsooin77.sync.employee.ExistingEmployeeSnapshot;
import com.kimsooin77.sync.audit.AuditLogRepository;
import com.kimsooin77.sync.integration.IntegrationTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.kimsooin77.sync.sync.PerformanceSqlInspector"
})
@Import(PostgreSqlTestConfiguration.class)
class EmployeeSyncBulkLookupIntegrationTest {

    @Autowired private EmployeeSyncService employeeSyncService;
    @Autowired private EmployeeBatchNormalizer normalizer;
    @Autowired private EmployeeSyncTransactionService employeeTransactions;
    @Autowired private SyncJobTransactionService jobTransactions;
    @Autowired private SyncExecutionGuard syncExecutionGuard;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private EmployeeComparator employeeComparator;
    @Autowired private SyncItemRepository syncItemRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private IntegrationTaskRepository integrationTaskRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE integration_attempt, integration_task, audit_log, sync_item, employee, sync_job RESTART IDENTITY CASCADE");
        PerformanceSqlInspector.clear();
    }

    @Test
    void loadsExistingEmployeesInChunksAt9991000And1001AndSkipHasNoIndividualSelect() {
        for (int count : List.of(999, 1_000, 1_001)) {
            jdbcTemplate.execute("TRUNCATE TABLE integration_attempt, integration_task, audit_log, sync_item, employee, sync_job RESTART IDENTITY CASCADE");
            seedEmployees(count);
            PerformanceSqlInspector.clear();

            SyncJobResult result = employeeSyncService.synchronize(rows(count));

            assertThat(result.skippedCount()).isEqualTo(count);
            assertThat(result.insertedCount()).isZero();
            assertThat(result.updatedCount()).isZero();
            assertThat(syncItemRepository.count()).isEqualTo(count);
            List<String> employeeSelects = employeeSelects();
            assertThat(employeeSelects).filteredOn(this::isIndividualLookup).isEmpty();
            assertThat(employeeSelects).filteredOn(this::isBulkLookup).hasSize((count + 999) / 1_000);
        }
    }

    @Test
    void doesNotQueryExistingEmployeesWhenEveryInputRowFailsNormalization() {
        SyncJobResult result = employeeSyncService.synchronize(List.of(
                hr("E-INVALID", "Invalid email", "not-an-email", "D1", "ACTIVE")));

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(employeeSelects()).isEmpty();
    }

    @Test
    void candidateIsRecheckedAgainstCurrentEmployeeInsideEmployeeTransaction() {
        Employee employee = employeeRepository.saveAndFlush(new Employee(
                "E-RECHECK", "Before", null, "D1", EmploymentStatus.ACTIVE));
        Map<String, com.kimsooin77.sync.employee.ExistingEmployeeSnapshot> baseline =
                employeeRepository.findSnapshotsByEmployeeNoIn(List.of("E-RECHECK")).stream()
                        .collect(java.util.stream.Collectors.toMap(ExistingEmployeeSnapshot::employeeNo, value -> value));
        assertThat(baseline).containsKey("E-RECHECK");
        NormalizedEmployee incoming = new NormalizedEmployee(
                "E-RECHECK", "Incoming", null, "D2", EmploymentStatus.ACTIVE);
        assertThat(employeeComparator.compare(baseline.get("E-RECHECK"), incoming).hasChanges()).isTrue();

        Employee current = employeeRepository.findByEmployeeNo("E-RECHECK").orElseThrow();
        current.updateSnapshot("Incoming", null, "D2", EmploymentStatus.ACTIVE);
        employeeRepository.saveAndFlush(current);
        Long jobId = jobTransactions.start(1);

        SyncItemResult result = employeeTransactions.processEmployee(jobId, 1, incoming);
        SyncJobResult completed = jobTransactions.complete(jobId);

        assertThat(result).isEqualTo(SyncItemResult.SKIPPED);
        assertThat(completed.skippedCount()).isEqualTo(1);
        assertThat(auditLogRepository.count()).isZero();
        assertThat(integrationTaskRepository.count()).isZero();
        assertThat(employeeRepository.findById(employee.getId())).isPresent();
    }

    @Test
    void legacyAndBulkImplementationsProduceEquivalentBusinessResults() {
        List<HrEmployeeResponse> rows = List.of(
                hr("E-NEW", "New", "new@example.com", "D1", "ACTIVE"),
                hr("E-UPDATE", "Updated", "updated@example.com", "D2", "ON_LEAVE"),
                hr("E-SKIP", "Same", "same@example.com", "D3", "ACTIVE"),
                hr("E-BAD", "Bad", "invalid", "D4", "ACTIVE"));
        seedComparisonEmployees();
        SyncJobResult legacy = new LegacyEmployeeSyncRunner(
                normalizer, employeeTransactions, jobTransactions, syncExecutionGuard).synchronize(rows);
        List<String> legacyState = businessState(legacy.id());

        jdbcTemplate.execute("TRUNCATE TABLE integration_attempt, integration_task, audit_log, sync_item, employee, sync_job RESTART IDENTITY CASCADE");
        seedComparisonEmployees();
        SyncJobResult optimized = employeeSyncService.synchronize(rows);

        assertThat(optimized.status()).isEqualTo(legacy.status());
        assertThat(optimized.totalCount()).isEqualTo(legacy.totalCount());
        assertThat(optimized.insertedCount()).isEqualTo(legacy.insertedCount());
        assertThat(optimized.updatedCount()).isEqualTo(legacy.updatedCount());
        assertThat(optimized.skippedCount()).isEqualTo(legacy.skippedCount());
        assertThat(optimized.failedCount()).isEqualTo(legacy.failedCount());
        assertThat(businessState(optimized.id())).containsExactlyElementsOf(legacyState);
    }

    private void seedEmployees(int count) {
        Instant now = Instant.now();
        jdbcTemplate.batchUpdate("""
                INSERT INTO employee (employee_no, name, company_email, department_code, employment_status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
                """, Stream.iterate(1, value -> value + 1).limit(count).toList(), 500,
                (PreparedStatement statement, Integer index) -> {
                    statement.setString(1, employeeNo(index));
                    statement.setString(2, name(index));
                    statement.setString(3, email(index));
                    statement.setString(4, "D0001");
                    statement.setTimestamp(5, Timestamp.from(now));
                    statement.setTimestamp(6, Timestamp.from(now));
                });
    }

    private void seedComparisonEmployees() {
        employeeRepository.saveAllAndFlush(List.of(
                new Employee("E-UPDATE", "Before", "before@example.com", "D1", EmploymentStatus.ACTIVE),
                new Employee("E-SKIP", "Same", "same@example.com", "D3", EmploymentStatus.ACTIVE)));
    }

    private List<String> businessState(Long jobId) {
        List<String> state = new ArrayList<>();
        state.addAll(jdbcTemplate.query("""
                SELECT employee_no, name, company_email, department_code, employment_status
                FROM employee ORDER BY employee_no
                """, (rs, row) -> "employee|" + rs.getString(1) + "|" + rs.getString(2) + "|"
                + rs.getString(3) + "|" + rs.getString(4) + "|" + rs.getString(5)));
        state.addAll(jdbcTemplate.query("""
                SELECT s.row_number, COALESCE(e.employee_no, s.employee_no), s.result
                FROM sync_item s LEFT JOIN employee e ON e.id=s.employee_id
                WHERE s.sync_job_id=? ORDER BY s.row_number
                """, (rs, row) -> "item|" + rs.getInt(1) + "|" + rs.getString(2) + "|" + rs.getString(3), jobId));
        state.addAll(jdbcTemplate.query("""
                SELECT s.row_number, e.employee_no, a.action, a.source, a.changes
                FROM audit_log a JOIN sync_item s ON s.id=a.sync_item_id
                JOIN employee e ON e.id=a.employee_id
                WHERE s.sync_job_id=? ORDER BY s.row_number
                """, (rs, row) -> "audit|" + rs.getInt(1) + "|" + rs.getString(2) + "|"
                + rs.getString(3) + "|" + rs.getString(4) + "|" + rs.getString(5), jobId));
        state.addAll(jdbcTemplate.query("""
                SELECT s.row_number, e.employee_no, t.action, t.status, t.payload
                FROM integration_task t JOIN sync_item s ON s.id=t.sync_item_id
                JOIN employee e ON e.id=t.employee_id
                WHERE s.sync_job_id=? ORDER BY s.row_number
                """, (rs, row) -> "task|" + rs.getInt(1) + "|" + rs.getString(2) + "|"
                + rs.getString(3) + "|" + rs.getString(4) + "|" + rs.getString(5), jobId));
        return state;
    }

    private List<HrEmployeeResponse> rows(int count) {
        return Stream.iterate(1, value -> value + 1).limit(count)
                .map(index -> hr(employeeNo(index), name(index), email(index), "D0001", "ACTIVE"))
                .toList();
    }

    private List<String> employeeSelects() {
        return PerformanceSqlInspector.snapshot().stream()
                .filter(sql -> sql.toLowerCase(Locale.ROOT).matches("(?s).*\\bfrom\\s+employee\\b.*"))
                .toList();
    }

    private boolean isIndividualLookup(String sql) {
        String normalized = sql.toLowerCase(Locale.ROOT);
        return normalized.matches("(?s).*\\bwhere\\b.*\\bemployee_no\\s*=\\s*\\?.*");
    }

    private boolean isBulkLookup(String sql) {
        String normalized = sql.toLowerCase(Locale.ROOT);
        return normalized.matches("(?s).*\\bwhere\\b.*\\bemployee_no\\s+in\\s*\\(.*");
    }

    private static String employeeNo(int index) { return "B" + String.format(Locale.ROOT, "%05d", index); }
    private static String name(int index) { return "Employee " + String.format(Locale.ROOT, "%05d", index); }
    private static String email(int index) { return String.format(Locale.ROOT, "employee%05d@example.com", index); }
    private static HrEmployeeResponse hr(String no, String name, String email, String dept, String status) {
        return new HrEmployeeResponse(no, name, email, dept, status);
    }
}
