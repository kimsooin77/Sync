package com.kimsooin77.sync.integration;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface IntegrationTaskRepository extends JpaRepository<IntegrationTask, Long> {

    List<IntegrationTask> findAllByEmployee_IdOrderByIdAsc(Long employeeId);

    List<IntegrationTask> findAllBySyncItem_Id(Long syncItemId);

    List<IntegrationTask> findByStatusOrderByCreatedAtAscIdAsc(IntegrationTaskStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from IntegrationTask task where task.id = :id "
            + "and task.status = :status")
    Optional<IntegrationTask> findByIdAndStatusForUpdate(
            @Param("id") Long id,
            @Param("status") IntegrationTaskStatus status
    );
}
