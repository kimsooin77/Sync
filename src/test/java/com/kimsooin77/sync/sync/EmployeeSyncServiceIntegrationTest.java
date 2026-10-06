package com.kimsooin77.sync.sync;

import com.kimsooin77.sync.employee.Employee;
import com.kimsooin77.sync.employee.EmployeeRepository;
import com.kimsooin77.sync.employee.EmploymentStatus;
import com.kimsooin77.sync.employee.PostgreSqlTestConfiguration;
import com.kimsooin77.sync.audit.AuditAction;
import com.kimsooin77.sync.audit.AuditLog;
import com.kimsooin77.sync.audit.AuditLogRepository;
import com.kimsooin77.sync.integration.IntegrationAction;
import com.kimsooin77.sync.integration.IntegrationTask;
import com.kimsooin77.sync.integration.IntegrationTaskRepository;
import com.kimsooin77.sync.integration.IntegrationTaskStatus;
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
    private AuditLogRepository auditLogRepository;

    @Autowired
    private IntegrationTaskRepository integrationTaskRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearDatabase() {
        dropTestTriggers();
        integrationTaskRepository.deleteAllInBatch();
        auditLogRepository.deleteAllInBatch();
        syncItemRepository.deleteAllInBatch();
        syncJobRepository.deleteAllInBatch();
        employeeRepository.deleteAllInBatch();
    }

    @AfterEach
    void cleanDatabase() {
        dropTestTriggers();
        integrationTaskRepository.deleteAllInBatch();
        auditLogRepository.deleteAllInBatch();
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
        AuditLog createdAudit = auditLogRepository.findAllByEmployee_IdOrderByIdAsc(saved.getId()).getFirst();
        assertThat(createdAudit.getAction()).isEqualTo(AuditAction.CREATED);
        assertThat(createdAudit.getChanges())
                .contains("\"employeeNo\":\"E-1001\"")
                .contains("\"departmentCode\":\"EnG\"");
        IntegrationTask createdTask = integrationTaskRepository
                .findAllByEmployee_IdOrderByIdAsc(saved.getId()).getFirst();
        assertThat(createdTask.getAction()).isEqualTo(IntegrationAction.CREATE_ACCOUNT);
        assertThat(createdTask.getStatus()).isEqualTo(IntegrationTaskStatus.PENDING);
        assertThat(createdTask.getIdempotencyKey()).isNotNull();
        assertThat(createdTask.getPayload()).contains("\"name\":\"Alice\"");

        SyncJobResult secondRun = employeeSyncService.synchronize(List.of(
                hr("E-1001", "Alice", "alice@example.com", "EnG", "ACTIVE")));

        assertThat(secondRun.status()).isEqualTo(SyncJobStatus.COMPLETED);
        assertThat(secondRun.insertedCount()).isZero();
        assertThat(secondRun.skippedCount()).isEqualTo(1);
        assertItem(secondRun.id(), 1, SyncItemResult.SKIPPED);
        assertThat(syncItemRepository.findAllBySyncJob_IdOrderByRowNumberAsc(secondRun.id()).getFirst()
                .getEmployeeId()).isEqualTo(saved.getId());
        assertThat(employeeRepository.count()).isEqualTo(1);
        assertThat(auditLogRepository.count()).isEqualTo(1);
        assertThat(integrationTaskRepository.count()).isEqualTo(1);
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
        AuditLog emailAudit = auditLogRepository.findAllBySyncItem_Id(
                syncItemRepository.findAllBySyncJob_IdOrderByRowNumberAsc(emailCleared.id()).getFirst().getId())
                .getFirst();
        assertThat(emailAudit.getAction()).isEqualTo(AuditAction.UPDATED);
        assertThat(emailAudit.getChanges())
                .contains("\"email\":{\"before\":\"old@example.com\",\"after\":null}")
                .doesNotContain("\"name\"");

        SyncJobResult departmentAndStatusChanged = employeeSyncService.synchronize(List.of(
                hr("E-2001", "New name", null, null, "ON_LEAVE")));

        assertThat(departmentAndStatusChanged.updatedCount()).isEqualTo(1);
        Employee updated = employeeRepository.findByEmployeeNo("E-2001").orElseThrow();
        assertThat(updated.getName()).isEqualTo("New name");
        assertThat(updated.getCompanyEmail()).isNull();
        assertThat(updated.getDepartmentCode()).isNull();
        assertThat(updated.getEmploymentStatus()).isEqualTo(EmploymentStatus.ON_LEAVE);
        assertItem(departmentAndStatusChanged.id(), 1, SyncItemResult.UPDATED);
        IntegrationTask updateTask = integrationTaskRepository.findAllBySyncItem_Id(
                syncItemRepository.findAllBySyncJob_IdOrderByRowNumberAsc(departmentAndStatusChanged.id())
                        .getFirst().getId()).getFirst();
        assertThat(updateTask.getAction()).isEqualTo(IntegrationAction.UPDATE_ACCOUNT);
        assertThat(updateTask.getPayload())
                .contains("\"name\":\"New name\"")
                .contains("\"employmentStatus\":\"ON_LEAVE\"");

        SyncJobResult unchanged = employeeSyncService.synchronize(List.of(
                hr("E-2001", "New name", "", "", "on_leave")));

        assertThat(unchanged.skippedCount()).isEqualTo(1);
        assertItem(unchanged.id(), 1, SyncItemResult.SKIPPED);
        assertThat(auditLogRepository.count()).isEqualTo(2);
        assertThat(integrationTaskRepository.count()).isEqualTo(2);
    }

    @Test
    void createsCreateTaskForNewOnLeaveEmployeeAndDisableTaskForNewTerminatedEmployee() {
        SyncJobResult onLeaveResult = employeeSyncService.synchronize(List.of(
                hr("E-2501", "On leave", null, null, "ON_LEAVE")));
        SyncJobResult terminatedResult = employeeSyncService.synchronize(List.of(
                hr("E-2502", "Terminated", null, null, "TERMINATED")));

        Employee onLeave = employeeRepository.findByEmployeeNo("E-2501").orElseThrow();
        Employee terminated = employeeRepository.findByEmployeeNo("E-2502").orElseThrow();
        assertThat(onLeaveResult.insertedCount()).isEqualTo(1);
        assertThat(terminatedResult.insertedCount()).isEqualTo(1);
        assertThat(auditLogRepository.findAllByEmployee_IdOrderByIdAsc(onLeave.getId()))
                .singleElement().extracting(AuditLog::getAction).isEqualTo(AuditAction.CREATED);
        assertThat(auditLogRepository.findAllByEmployee_IdOrderByIdAsc(terminated.getId()))
                .singleElement().extracting(AuditLog::getAction).isEqualTo(AuditAction.CREATED);
        assertThat(integrationTaskRepository.findAllByEmployee_IdOrderByIdAsc(onLeave.getId()))
                .singleElement().extracting(IntegrationTask::getAction)
                .isEqualTo(IntegrationAction.CREATE_ACCOUNT);
        assertThat(integrationTaskRepository.findAllByEmployee_IdOrderByIdAsc(terminated.getId()))
                .singleElement().extracting(IntegrationTask::getAction)
                .isEqualTo(IntegrationAction.DISABLE_ACCOUNT);
    }

    @Test
    void terminationCreatesDisableTaskAndEarlierTaskPayloadKeepsItsSnapshot() {
        Employee employee = employeeRepository.saveAndFlush(new Employee(
                "E-2601", "Before", "before@example.com", "DEV01", EmploymentStatus.ACTIVE));

        SyncJobResult changed = employeeSyncService.synchronize(List.of(
                hr("E-2601", "After", "after@example.com", "DEV02", "TERMINATED")));

        List<IntegrationTask> tasks = integrationTaskRepository.findAllByEmployee_IdOrderByIdAsc(employee.getId());
        assertThat(tasks).hasSize(1);
        assertThat(tasks.getFirst().getAction()).isEqualTo(IntegrationAction.DISABLE_ACCOUNT);
        assertThat(tasks.getFirst().getPayload())
                .contains("\"name\":\"After\"")
                .contains("\"departmentCode\":\"DEV02\"")
                .contains("\"employmentStatus\":\"TERMINATED\"");

        employeeSyncService.synchronize(List.of(
                hr("E-2601", "Rehired", "rehired@example.com", "DEV03", "ACTIVE")));

        List<IntegrationTask> afterRehire = integrationTaskRepository.findAllByEmployee_IdOrderByIdAsc(employee.getId());
        assertThat(afterRehire).hasSize(2);
        assertThat(afterRehire.get(1).getAction()).isEqualTo(IntegrationAction.UPDATE_ACCOUNT);
        assertThat(afterRehire.getFirst().getPayload())
                .contains("\"name\":\"After\"")
                .contains("\"departmentCode\":\"DEV02\"")
                .doesNotContain("Rehired", "DEV03");
        AuditLog updatedAudit = auditLogRepository.findAllBySyncItem_Id(
                syncItemRepository.findAllBySyncJob_IdOrderByRowNumberAsc(changed.id()).getFirst().getId())
                .getFirst();
        assertThat(updatedAudit.getAction()).isEqualTo(AuditAction.UPDATED);
        assertThat(updatedAudit.getChanges())
                .contains("\"name\":{\"before\":\"Before\",\"after\":\"After\"}")
                .contains("\"email\":{\"before\":\"before@example.com\",\"after\":\"after@example.com\"}")
                .contains("\"departmentCode\":{\"before\":\"DEV01\",\"after\":\"DEV02\"}")
                .contains("\"employmentStatus\":{\"before\":\"ACTIVE\",\"after\":\"TERMINATED\"}");
    }

    @Test
    void idempotencyKeyIsUniqueInPostgres() {
        SyncJobResult result = employeeSyncService.synchronize(List.of(
                hr("E-2701", "One", null, null, "ACTIVE"),
                hr("E-2702", "Two", null, null, "ACTIVE")));
        List<SyncItem> items = syncItemRepository.findAllBySyncJob_IdOrderByRowNumberAsc(result.id());
        List<IntegrationTask> tasks = items.stream()
                .map(item -> integrationTaskRepository.findAllBySyncItem_Id(item.getId()).getFirst())
                .toList();
        IntegrationTask duplicate = tasks.getFirst();
        SyncItem secondItem = items.get(1);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO integration_task (
                    employee_id, sync_item_id, target, action, status, payload, idempotency_key,
                    retry_count, max_retry_count, created_at, updated_at
                ) VALUES (?, ?, 'GROUPWARE', 'CREATE_ACCOUNT', 'PENDING', '{}', ?, 0, 3, now(), now())
                """, secondItem.getEmployeeId(), secondItem.getId(), duplicate.getIdempotencyKey()))
                .isInstanceOf(DataAccessException.class);
        assertThat(integrationTaskRepository.count()).isEqualTo(2);
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
        assertThat(auditLogRepository.count()).isEqualTo(2);
        assertThat(integrationTaskRepository.count()).isEqualTo(2);
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
                hr("E-5001", "N".repeat(201), null, null, "ACTIVE"),
                hr("E-5002", "After", null, null, "ACTIVE")));

        assertThat(result.status()).isEqualTo(SyncJobStatus.COMPLETED_WITH_ERRORS);
        assertThat(result.insertedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(employeeRepository.findByEmployeeNo("E-5001")).isEmpty();
        assertThat(employeeRepository.findByEmployeeNo("E-5002")).isPresent();

        List<SyncItem> items = syncItemRepository.findAllBySyncJob_IdOrderByRowNumberAsc(result.id());
        assertThat(items).extracting(SyncItem::getResult)
                .containsExactly(SyncItemResult.FAILED, SyncItemResult.INSERTED);
        assertThat(items.getFirst().getErrorCode()).isEqualTo("DATA_INTEGRITY_VIOLATION");
        assertThat(auditLogRepository.count()).isEqualTo(1);
        assertThat(integrationTaskRepository.count()).isEqualTo(1);
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
        assertThat(auditLogRepository.count()).isZero();
        assertThat(integrationTaskRepository.count()).isZero();
    }

    @Test
    void auditFailureRollsBackEmployeeAndSuccessItem() {
        createRejectAuditLogTrigger();

        SyncJobResult result = employeeSyncService.synchronize(List.of(
                hr("E-6501", "Audit failure", null, null, "ACTIVE")));

        assertThat(result.status()).isEqualTo(SyncJobStatus.COMPLETED_WITH_ERRORS);
        assertThat(employeeRepository.findByEmployeeNo("E-6501")).isEmpty();
        assertThat(auditLogRepository.count()).isZero();
        assertThat(integrationTaskRepository.count()).isZero();
        List<SyncItem> items = syncItemRepository.findAllBySyncJob_IdOrderByRowNumberAsc(result.id());
        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.getResult()).isEqualTo(SyncItemResult.FAILED);
            assertThat(item.getEmployeeId()).isNull();
        });
    }

    @Test
    void integrationTaskFailureRollsBackEmployeeSyncItemAndAudit() {
        createRejectIntegrationTaskTrigger();

        SyncJobResult result = employeeSyncService.synchronize(List.of(
                hr("E-6601", "Task failure", null, null, "ACTIVE")));

        assertThat(result.status()).isEqualTo(SyncJobStatus.COMPLETED_WITH_ERRORS);
        assertThat(employeeRepository.findByEmployeeNo("E-6601")).isEmpty();
        assertThat(auditLogRepository.count()).isZero();
        assertThat(integrationTaskRepository.count()).isZero();
        List<SyncItem> items = syncItemRepository.findAllBySyncJob_IdOrderByRowNumberAsc(result.id());
        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.getResult()).isEqualTo(SyncItemResult.FAILED);
            assertThat(item.getEmployeeId()).isNull();
        });
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

        SyncJobResult afterDatabaseFailure = employeeSyncService.synchronize(List.of(
                hr("E-7002", "Sync after system failure", null, null, "ACTIVE")));
        assertThat(afterDatabaseFailure.status()).isEqualTo(SyncJobStatus.COMPLETED);
        assertThat(afterDatabaseFailure.insertedCount()).isEqualTo(1);
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

    private void createRejectAuditLogTrigger() {
        jdbcTemplate.execute("""
                CREATE FUNCTION test_reject_audit_log() RETURNS trigger
                LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE check_violation USING MESSAGE = 'test rejects audit log';
                END;
                $$
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER test_reject_audit_log
                BEFORE INSERT ON audit_log
                FOR EACH ROW EXECUTE FUNCTION test_reject_audit_log()
                """);
    }

    private void createRejectIntegrationTaskTrigger() {
        jdbcTemplate.execute("""
                CREATE FUNCTION test_reject_integration_task() RETURNS trigger
                LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE check_violation USING MESSAGE = 'test rejects integration task';
                END;
                $$
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER test_reject_integration_task
                BEFORE INSERT ON integration_task
                FOR EACH ROW EXECUTE FUNCTION test_reject_integration_task()
                """);
    }

    private void dropTestTriggers() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS test_reject_inserted_sync_item ON sync_item");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS test_reject_inserted_sync_item()");
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS test_reject_audit_log ON audit_log");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS test_reject_audit_log()");
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS test_reject_integration_task ON integration_task");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS test_reject_integration_task()");
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
