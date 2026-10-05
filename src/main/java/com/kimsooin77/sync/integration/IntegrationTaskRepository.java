package com.kimsooin77.sync.integration;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface IntegrationTaskRepository extends JpaRepository<IntegrationTask, Long> {

    List<IntegrationTask> findAllByEmployee_IdOrderByIdAsc(Long employeeId);

    List<IntegrationTask> findAllBySyncItem_Id(Long syncItemId);

    @Query("select task from IntegrationTask task where task.status = :pending "
            + "or (task.status = :retryWait and task.nextRetryAt <= :now) "
            + "order by task.createdAt asc, task.id asc")
    List<IntegrationTask> findEligibleTasks(
            @Param("pending") IntegrationTaskStatus pending,
            @Param("retryWait") IntegrationTaskStatus retryWait,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from IntegrationTask task where task.id = :id "
            + "and (task.status = :pending or (task.status = :retryWait and task.nextRetryAt <= :now))")
    Optional<IntegrationTask> findEligibleByIdForUpdate(
            @Param("id") Long id,
            @Param("pending") IntegrationTaskStatus pending,
            @Param("retryWait") IntegrationTaskStatus retryWait,
            @Param("now") Instant now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from IntegrationTask task where task.id = :id and task.status = :processing")
    Optional<IntegrationTask> findProcessingByIdForUpdate(
            @Param("id") Long id,
            @Param("processing") IntegrationTaskStatus processing
    );
}
