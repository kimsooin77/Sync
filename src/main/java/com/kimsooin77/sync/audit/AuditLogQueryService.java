package com.kimsooin77.sync.audit;
import com.kimsooin77.sync.employee.EmployeeNotFoundException;
import com.kimsooin77.sync.employee.EmployeeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class AuditLogQueryService {
    private final AuditLogRepository audits;
    private final EmployeeRepository employees;
    public AuditLogQueryService(AuditLogRepository audits, EmployeeRepository employees) { this.audits = audits; this.employees = employees; }
    @Transactional(readOnly = true)
    public Page<AuditLog> findForEmployee(Long employeeId, Pageable pageable) {
        if (!employees.existsById(employeeId)) throw new EmployeeNotFoundException(employeeId);
        return audits.findAllByEmployee_IdOrderByCreatedAtDescIdDesc(employeeId, pageable);
    }
}
