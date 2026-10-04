package com.kimsooin77.sync.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findAllByEmployee_IdOrderByIdAsc(Long employeeId);

    List<AuditLog> findAllBySyncItem_Id(Long syncItemId);
}
