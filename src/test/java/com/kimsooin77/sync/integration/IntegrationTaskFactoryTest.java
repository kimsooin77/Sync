package com.kimsooin77.sync.integration;

import com.kimsooin77.sync.employee.Employee;
import com.kimsooin77.sync.employee.EmploymentStatus;
import com.kimsooin77.sync.sync.Day4TestFixtures;
import com.kimsooin77.sync.sync.EmployeeChangeSet;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationTaskFactoryTest {

    private final IntegrationTaskFactory factory = new IntegrationTaskFactory(
            JsonMapper.builder().build(), new IdempotencyKeyGenerator());

    @Test
    void createsAccountTasksForActiveAndOnLeaveAndDisableTaskForNewTerminatedEmployees() {
        Employee activeEmployee = employee("E-2001", EmploymentStatus.ACTIVE);
        IntegrationTask active = factory.forInserted(
                activeEmployee, Day4TestFixtures.insertedItem(activeEmployee)).orElseThrow();
        Employee onLeaveEmployee = employee("E-2002", EmploymentStatus.ON_LEAVE);
        IntegrationTask onLeave = factory.forInserted(
                onLeaveEmployee, Day4TestFixtures.insertedItem(onLeaveEmployee)).orElseThrow();
        Employee terminatedEmployee = employee("E-2003", EmploymentStatus.TERMINATED);

        assertThat(active.getAction()).isEqualTo(IntegrationAction.CREATE_ACCOUNT);
        assertThat(onLeave.getAction()).isEqualTo(IntegrationAction.CREATE_ACCOUNT);
        IntegrationTask terminated = factory.forInserted(
                terminatedEmployee, Day4TestFixtures.insertedItem(terminatedEmployee)).orElseThrow();
        assertThat(terminated.getAction()).isEqualTo(IntegrationAction.DISABLE_ACCOUNT);
        assertNewTaskDefaults(active);
        assertNewTaskDefaults(terminated);
    }

    @Test
    void selectsDisableForTerminatedFinalStateAndUpdateForRehire() {
        Employee employee = employee("E-3001", EmploymentStatus.TERMINATED);
        EmployeeChangeSet termination = new EmployeeChangeSet(List.of(
                new EmployeeChangeSet.FieldChange(
                        "employmentStatus", EmploymentStatus.ACTIVE, EmploymentStatus.TERMINATED)));
        IntegrationTask disable = factory.forUpdated(
                employee, Day4TestFixtures.updatedItem(employee), termination);
        assertThat(disable.getAction()).isEqualTo(IntegrationAction.DISABLE_ACCOUNT);

        employee.updateSnapshot("Alice", null, null, EmploymentStatus.ACTIVE);
        EmployeeChangeSet rehire = new EmployeeChangeSet(List.of(
                new EmployeeChangeSet.FieldChange(
                        "employmentStatus", EmploymentStatus.TERMINATED, EmploymentStatus.ACTIVE)));
        IntegrationTask update = factory.forUpdated(
                employee, Day4TestFixtures.updatedItem(employee), rehire);
        assertThat(update.getAction()).isEqualTo(IntegrationAction.UPDATE_ACCOUNT);

        employee.updateSnapshot("Changed while terminated", null, null, EmploymentStatus.TERMINATED);
        EmployeeChangeSet terminatedProfileChange = new EmployeeChangeSet(List.of(
                new EmployeeChangeSet.FieldChange("name", "Alice", "Changed while terminated")));
        IntegrationTask repeatedDisable = factory.forUpdated(
                employee, Day4TestFixtures.updatedItem(employee), terminatedProfileChange);
        assertThat(repeatedDisable.getAction()).isEqualTo(IntegrationAction.DISABLE_ACCOUNT);
    }

    @Test
    void createsUuidKeyAndImmutableSnapshotPayloadWithPendingDefaults() {
        Employee employee = new Employee(
                "E-4001", "Alice", "alice@example.com", "DEV01", EmploymentStatus.ACTIVE);
        IntegrationTask task = factory.forInserted(
                employee, Day4TestFixtures.insertedItem(employee)).orElseThrow();
        UUID key = task.getIdempotencyKey();

        assertThat(key).isNotNull();
        assertThat(key.version()).isEqualTo(4);
        assertThat(task.getStatus()).isEqualTo(IntegrationTaskStatus.PENDING);
        assertThat(task.getRetryCount()).isZero();
        assertThat(task.getMaxRetryCount()).isEqualTo(3);
        assertThat(task.getNextRetryAt()).isNull();
        assertThat(task.getPayload())
                .contains("\"employeeNo\":\"E-4001\"")
                .contains("\"name\":\"Alice\"")
                .contains("\"email\":\"alice@example.com\"")
                .contains("\"departmentCode\":\"DEV01\"")
                .contains("\"employmentStatus\":\"ACTIVE\"");

        employee.updateSnapshot("Alice", "alice@example.com", "DEV02", EmploymentStatus.ACTIVE);
        assertThat(task.getPayload()).contains("\"departmentCode\":\"DEV01\"")
                .doesNotContain("DEV02");
        assertThat(task.getIdempotencyKey()).isEqualTo(key);
    }

    private static void assertNewTaskDefaults(IntegrationTask task) {
        assertThat(task.getTarget()).isEqualTo(IntegrationTarget.GROUPWARE);
        assertThat(task.getStatus()).isEqualTo(IntegrationTaskStatus.PENDING);
        assertThat(task.getRetryCount()).isZero();
        assertThat(task.getMaxRetryCount()).isEqualTo(3);
        assertThat(task.getNextRetryAt()).isNull();
        assertThat(task.getLastErrorCode()).isNull();
        assertThat(task.getLastErrorMessage()).isNull();
    }

    private static Employee employee(String employeeNo, EmploymentStatus status) {
        return new Employee(employeeNo, "Alice", null, null, status);
    }
}
