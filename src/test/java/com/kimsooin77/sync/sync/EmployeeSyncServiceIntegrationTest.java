package com.kimsooin77.sync.sync;

import com.kimsooin77.sync.employee.Employee;
import com.kimsooin77.sync.employee.EmployeeRepository;
import com.kimsooin77.sync.employee.EmploymentStatus;
import com.kimsooin77.sync.employee.PostgreSqlTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(PostgreSqlTestConfiguration.class)
class EmployeeSyncServiceIntegrationTest {

    @Autowired
    private EmployeeSyncService employeeSyncService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private SyncJobRepository syncJobRepository;

    @Autowired
    private SyncItemRepository syncItemRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearDatabase() {
        dropSyncItemInsertTrigger();
        syncItemRepository.deleteAllInBatch();
        syncJobRepository.deleteAllInBatch();
        employeeRepository.deleteAllInBatch();
    }

    @AfterEach
    void cleanDatabase() {
        dropSyncItemInsertTrigger();
        syncItemRepository.deleteAllInBatch();
        syncJobRepository.deleteAllInBatch();
        employeeRepository.deleteAllInBatch();
    }

    @Test
    void insertsEmployeeAndRepeatingSameSnapshotSkipsIt() {
        SyncJobResult firstRun = employeeSyncService.synchronize(List.of(
                hr(" e-1001 ", " Alice ", " ALICE@EXAMPLE.COM ", " EnG ", "active")));

        assertThat(firstRun.status()).isEqualTo(SyncJobStatus.COMPLETED);
        assertThat(firstRun.totalCount()).isEqualTo(1);
        assertThat(firstRun.insertedCount()).isEqualTo(1);
        assertThat(firstRun.updatedCount()).isZero();
        assertThat(firstRun.skippedCount()).isZero();
        assertThat(firstRun.failedCount()).isZero();
        assertThat(firstRun.finishedAt()).isNotNull();
        assertItem(firstRun.id(), 1, SyncItemResult.INSERTED);

        Employee saved = employeeRepository.findByEmployeeNo("E-1001").orElseThrow();
        assertThat(saved.getName()).isEqualTo("Alice");
        assertThat(saved.getCompanyEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getDepartmentCode()).isEqualTo("EnG");
        assertThat(saved.getEmploymentStatus()).isEqualTo(EmploymentStatus.ACTIVE);
        assertThat(syncItemRepository.findAllBySyncJob_IdOrderByRowNumberAsc(firstRun.id()).getFirst()
                .getEmployeeId()).isEqualTo(saved.getId());

        SyncJobResult secondRun = employeeSyncService.synchronize(List.of(
                hr("E-1001", "Alice", "alice@example.com", "EnG", "ACTIVE")));

        assertThat(secondRun.status()).isEqualTo(SyncJobStatus.COMPLETED);
        assertThat(secondRun.insertedCount()).isZero();
        assertThat(secondRun.skippedCount()).isEqualTo(1);
        assertItem(secondRun.id(), 1, SyncItemResult.SKIPPED);
        assertThat(syncItemRepository.findAllBySyncJob_IdOrderByRowNumberAsc(secondRun.id()).getFirst()
                .getEmployeeId()).isEqualTo(saved.getId());
        assertThat(employeeRepository.count()).isEqualTo(1);
    }

    @Test
    void updatesChangedFieldsAndCanClearOptionalFields() {
        employeeRepository.saveAndFlush(new Employee(
                "E-2001", "Old name", "old@example.com", "OPS", EmploymentStatus.ACTIVE));

        SyncJobResult emailCleared = employeeSyncService.synchronize(List.of(
                hr("E-2001", "Old name", " ", "OPS", "ACTIVE")));

        assertThat(emailCleared.updatedCount()).isEqualTo(1);
        assertThat(emailCleared.skippedCount()).isZero();
        Employee afterEmailChange = employeeRepository.findByEmployeeNo("E-2001").orElseThrow();
        assertThat(afterEmailChange.getCompanyEmail()).isNull();
        assertThat(afterEmailChange.getDepartmentCode()).isEqualTo("OPS");
        assertItem(emailCleared.id(), 1, SyncItemResult.UPDATED);
        assertThat(syncItemRepository.findAllBySyncJob_IdOrderByRowNumberAsc(emailCleared.id()).getFirst()
                .getEmployeeId()).isEqualTo(afterEmailChange.getId());

        SyncJobResult departmentAndStatusChanged = employeeSyncService.synchronize(List.of(
                hr("E-2001", "New name", null, null, "ON_LEAVE")));

        assertThat(departmentAndStatusChanged.updatedCount()).isEqualTo(1);
        Employee updated = employeeRepository.findByEmployeeNo("E-2001").orElseThrow();
        assertThat(updated.getName()).isEqualTo("New name");
        assertThat(updated.getCompanyEmail()).isNull();
        assertThat(updated.getDepartmentCode()).isNull();
        assertThat(updated.getEmploymentStatus()).isEqualTo(EmploymentStatus.ON_LEAVE);
        assertItem(departmentAndStatusChanged.id(), 1, SyncItemResult.UPDATED);

        SyncJobResult unchanged = employeeSyncService.synchronize(List.of(
                hr("E-2001", "New name", "", "", "on_leave")));

        assertThat(unchanged.skippedCount()).isEqualTo(1);
        assertItem(unchanged.id(), 1, SyncItemResult.SKIPPED);
    }

    @Test
    void normalizationFailureIsRecordedAndDoesNotStopOtherRows() {
        SyncJobResult result = employeeSyncService.synchronize(List.of(
                hr("E-3001", "Before", null, null, "ACTIVE"),
                hr("E-3002", "Invalid", "not-an-email", null, "ACTIVE"),
                hr("E-3003", "After", null, null, "ACTIVE")));

        assertThat(result.status()).isEqualTo(SyncJobStatus.COMPLETED_WITH_ERRORS);
        assertThat(result.totalCount()).isEqualTo(3);
        assertThat(result.insertedCount()).isEqualTo(2);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(employeeRepository.findByEmployeeNo("E-3001")).isPresent();
        assertThat(employeeRepository.findByEmployeeNo("E-3002")).isEmpty();
        assertThat(employeeRepository.findByEmployeeNo("E-3003")).isPresent();

        List<SyncItem> items = syncItemRepository.findAllBySyncJob_IdOrderByRowNumberAsc(result.id());
        assertThat(items).extracting(SyncItem::getResult)
                .containsExactly(SyncItemResult.INSERTED, SyncItemResult.FAILED, SyncItemResult.INSERTED);
        assertThat(items.get(1).getErrorCode()).isEqualTo("INVALID_EMAIL");
        assertThat(items.get(1).getEmployeeId()).isNull();
    }

    @Test
    void allRowsWithCanonicalDuplicateEmployeeNumbersFail() {
        SyncJobResult result = employeeSyncService.synchronize(List.of(
                hr(" e-4001", "One", null, null, "ACTIVE"),
                hr("E-4001 ", "Two", null, null, "ACTIVE"),
                hr("E-4002", "Three", null, null, "ACTIVE")));

        assertThat(result.status()).isEqualTo(SyncJobStatus.COMPLETED_WITH_ERRORS);
        assertThat(result.insertedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(2);
        assertThat(employeeRepository.findByEmployeeNo("E-4001")).isEmpty();
        assertThat(employeeRepository.findByEmployeeNo("E-4002")).isPresent();
        List<SyncItem> items = syncItemRepository.findAllBySyncJob_IdOrderByRowNumberAsc(result.id());
        assertThat(items).extracting(SyncItem::getResult)
                .containsExactly(SyncItemResult.FAILED, SyncItemResult.FAILED, SyncItemResult.INSERTED);
        assertThat(items.get(0).getEmployeeNo()).isEqualTo("E-4001");
        assertThat(items.get(1).getEmployeeNo()).isEqualTo("E-4001");
    }

    @Test
    void databaseConstraintFailureIsIsolatedAndOtherEmployeesCommit() {
        SyncJobResult result = employeeSyncService.synchronize(List.of(
                hr("E-5001", "Before", null, null, "ACTIVE"),
                hr("E-5002", "N".repeat(201), null, null, "ACTIVE"),
                hr("E-5003", "After", null, null, "ACTIVE")));

        assertThat(result.status()).isEqualTo(SyncJobStatus.COMPLETED_WITH_ERRORS);
        assertThat(result.insertedCount()).isEqualTo(2);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(employeeRepository.findByEmployeeNo("E-5001")).isPresent();
        assertThat(employeeRepository.findByEmployeeNo("E-5002")).isEmpty();
        assertThat(employeeRepository.findByEmployeeNo("E-5003")).isPresent();

        List<SyncItem> items = syncItemRepository.findAllBySyncJob_IdOrderByRowNumberAsc(result.id());
        assertThat(items).extracting(SyncItem::getResult)
                .containsExactly(SyncItemResult.INSERTED, SyncItemResult.FAILED, SyncItemResult.INSERTED);
        assertThat(items.get(1).getErrorCode()).isEqualTo("DATA_INTEGRITY_VIOLATION");
    }

    @Test
    void rollsBackEmployeeWhenInsertedSyncItemCannotBeSaved() {
        createRejectInsertedSyncItemTrigger();

        SyncJobResult result = employeeSyncService.synchronize(List.of(
                hr("E-6001", "Rollback", null, null, "ACTIVE")));

        assertThat(result.status()).isEqualTo(SyncJobStatus.COMPLETED_WITH_ERRORS);
        assertThat(result.insertedCount()).isZero();
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(employeeRepository.findByEmployeeNo("E-6001")).isEmpty();
        List<SyncItem> items = syncItemRepository.findAllBySyncJob_IdOrderByRowNumberAsc(result.id());
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getResult()).isEqualTo(SyncItemResult.FAILED);
        assertThat(items.get(0).getErrorCode()).isEqualTo("DATA_INTEGRITY_VIOLATION");
        assertThat(items.get(0).getEmployeeId()).isNull();
    }

    @Test
    void marksJobFailedAndRethrowsSystemDatabaseErrors() {
        createRaiseEmployeeSystemErrorTrigger();

        assertThatThrownBy(() -> employeeSyncService.synchronize(List.of(
                hr("E-7000", "Committed before system failure", null, null, "ACTIVE"),
                hr("E-7001", "System failure", null, null, "ACTIVE"))))
                .isInstanceOf(DataAccessException.class);

        SyncJob job = syncJobRepository.findAll().getFirst();
        assertThat(job.getStatus()).isEqualTo(SyncJobStatus.FAILED);
        assertThat(job.getTotalCount()).isEqualTo(2);
        assertThat(job.getInsertedCount()).isEqualTo(1);
        assertThat(job.getFailedCount()).isZero();
        assertThat(job.getFinishedAt()).isNotNull();
        Employee committed = employeeRepository.findByEmployeeNo("E-7000").orElseThrow();
        assertThat(employeeRepository.findByEmployeeNo("E-7001")).isEmpty();
        List<SyncItem> items = syncItemRepository.findAllBySyncJob_IdOrderByRowNumberAsc(job.getId());
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().getResult()).isEqualTo(SyncItemResult.INSERTED);
        assertThat(items.getFirst().getEmployeeId()).isEqualTo(committed.getId());
    }

    @Test
    void emptyInputCompletesWithZeroCounts() {
        SyncJobResult result = employeeSyncService.synchronize(List.of());

        assertThat(result.status()).isEqualTo(SyncJobStatus.COMPLETED);
        assertThat(result.totalCount()).isZero();
        assertThat(result.insertedCount()).isZero();
        assertThat(result.updatedCount()).isZero();
        assertThat(result.skippedCount()).isZero();
        assertThat(result.failedCount()).isZero();
        assertThat(result.finishedAt()).isNotNull();
        assertThat(syncItemRepository.findAllBySyncJob_IdOrderByRowNumberAsc(result.id())).isEmpty();
    }

    private void assertItem(Long syncJobId, int rowNumber, SyncItemResult expectedResult) {
        List<SyncItem> items = syncItemRepository.findAllBySyncJob_IdOrderByRowNumberAsc(syncJobId);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getRowNumber()).isEqualTo(rowNumber);
        assertThat(items.get(0).getResult()).isEqualTo(expectedResult);
        assertThat(items.get(0).getCreatedAt()).isNotNull();
    }

    private void createRejectInsertedSyncItemTrigger() {
        jdbcTemplate.execute("""
                CREATE FUNCTION test_reject_inserted_sync_item() RETURNS trigger
                LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.result = 'INSERTED' THEN
                        RAISE check_violation USING MESSAGE = 'test rejects inserted sync item';
                    END IF;
                    RETURN NEW;
                END;
                $$
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER test_reject_inserted_sync_item
                BEFORE INSERT ON sync_item
                FOR EACH ROW EXECUTE FUNCTION test_reject_inserted_sync_item()
                """);
    }

    private void createRaiseEmployeeSystemErrorTrigger() {
        jdbcTemplate.execute("""
                CREATE FUNCTION test_raise_employee_system_error() RETURNS trigger
                LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.employee_no = 'E-7001' THEN
                        RAISE EXCEPTION 'test database system error' USING ERRCODE = '58000';
                    END IF;
                    RETURN NEW;
                END;
                $$
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER test_raise_employee_system_error
                BEFORE INSERT ON employee
                FOR EACH ROW EXECUTE FUNCTION test_raise_employee_system_error()
                """);
    }

    private void dropSyncItemInsertTrigger() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS test_reject_inserted_sync_item ON sync_item");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS test_reject_inserted_sync_item()");
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS test_raise_employee_system_error ON employee");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS test_raise_employee_system_error()");
    }

    private static HrEmployeeResponse hr(
            String employeeNo,
            String name,
            String email,
            String departmentCode,
            String status
    ) {
        return new HrEmployeeResponse(employeeNo, name, email, departmentCode, status);
    }
}
