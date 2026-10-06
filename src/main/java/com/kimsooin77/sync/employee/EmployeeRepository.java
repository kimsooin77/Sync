package com.kimsooin77.sync.employee;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmployeeNo(String employeeNo);

    @Query("select new com.kimsooin77.sync.employee.EmployeeListRow(e.id, e.employeeNo, e.name, e.companyEmail, "
            + "e.departmentCode, e.employmentStatus, e.createdAt, e.updatedAt) from Employee e "
            + "where (:keyword = '' or lower(e.employeeNo) like lower(concat('%', :keyword, '%')) "
            + "or lower(e.name) like lower(concat('%', :keyword, '%'))) "
            + "and (:status is null or e.employmentStatus = :status)")
    Page<EmployeeListRow> search(@Param("keyword") String keyword,
                                 @Param("status") EmploymentStatus status, Pageable pageable);
}
