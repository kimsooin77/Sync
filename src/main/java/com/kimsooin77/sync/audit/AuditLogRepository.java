package com.kimsooin77.sync.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findAllByEmployee_IdOrderByIdAsc(Long employeeId);

    Page<AuditLog> findAllByEmployee_IdOrderByCreatedAtDescIdDesc(Long employeeId, Pageable pageable);

    List<AuditLog> findAllBySyncItem_Id(Long syncItemId);
}
