package com.kimsooin77.sync.employee;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(PostgreSqlTestConfiguration.class)
class EmployeeRepositoryTest {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Test
    void savesEmployeeAndFindsItByEmployeeNumber() {
        Employee saved = employeeRepository.saveAndFlush(employee(
                "E-1001", "홍길동", "hong@example.com", "ENG", EmploymentStatus.ACTIVE));

        Employee found = employeeRepository.findByEmployeeNo("E-1001").orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getName()).isEqualTo("홍길동");
        assertThat(found.getEmploymentStatus()).isEqualTo(EmploymentStatus.ACTIVE);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void fullSnapshotCanClearPreviouslyAssignedOptionalFields() {
        Employee employee = employeeRepository.saveAndFlush(employee(
                "E-1002", "김수인", "sooin@example.com", "OPS", EmploymentStatus.ON_LEAVE));

        employee.updateSnapshot("김수인", null, null, EmploymentStatus.ACTIVE);
        employeeRepository.flush();

        Employee reloaded = employeeRepository.findByEmployeeNo("E-1002").orElseThrow();
        assertThat(reloaded.getCompanyEmail()).isNull();
        assertThat(reloaded.getDepartmentCode()).isNull();
        assertThat(reloaded.getEmploymentStatus()).isEqualTo(EmploymentStatus.ACTIVE);
    }

    @Test
    void employeeNumberMustBeUnique() {
        employeeRepository.saveAndFlush(employee(
                "E-1003", "이하나", null, null, EmploymentStatus.ACTIVE));
        Employee duplicate = employee("E-1003", "박둘", null, null, EmploymentStatus.ACTIVE);

        assertThatThrownBy(() -> employeeRepository.saveAndFlush(duplicate))
                .isInstanceOf(RuntimeException.class);
    }

    private static Employee employee(
            String employeeNo,
            String name,
            String email,
            String departmentCode,
            EmploymentStatus status
    ) {
        return new Employee(employeeNo, name, email, departmentCode, status);
    }
}
