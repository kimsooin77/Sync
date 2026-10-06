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
import org.springframework.data.domain.Page;

public interface IntegrationTaskRepository extends JpaRepository<IntegrationTask, Long> {

    @Query("select new com.kimsooin77.sync.integration.IntegrationTaskListRow(task.id, employee.employeeNo, "
            + "task.action, task.status, task.retryCount, task.maxRetryCount, task.lastErrorCode, "
            + "task.createdAt, task.updatedAt) from IntegrationTask task join task.employee employee "
            + "where (:status is null or task.status = :status) and (:action is null or task.action = :action) "
            + "and (:employeeNo is null or employee.employeeNo = :employeeNo)")
    Page<IntegrationTaskListRow> search(@Param("status") IntegrationTaskStatus status,
                                        @Param("action") IntegrationAction action,
                                        @Param("employeeNo") String employeeNo, Pageable pageable);

    @Query("select new com.kimsooin77.sync.integration.IntegrationTaskDetailRow(task.id, employee.employeeNo, "
            + "task.target, task.action, task.status, task.payload, task.idempotencyKey, task.retryCount, "
            + "task.maxRetryCount, task.nextRetryAt, task.processingStartedAt, task.lastErrorCode, "
            + "task.lastErrorMessage, task.createdAt, task.updatedAt) from IntegrationTask task "
            + "join task.employee employee where task.id = :id")
    Optional<IntegrationTaskDetailRow> findDetailById(@Param("id") Long id);

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

    @Query("select task.id from IntegrationTask task where task.status = :processing "
            + "and task.processingStartedAt <= :cutoff order by task.processingStartedAt asc, task.id asc")
    List<Long> findStaleProcessingIds(
            @Param("processing") IntegrationTaskStatus processing,
            @Param("cutoff") Instant cutoff,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from IntegrationTask task where task.id = :id and task.status = :processing "
            + "and task.processingStartedAt <= :cutoff")
    Optional<IntegrationTask> findStaleProcessingByIdForUpdate(
            @Param("id") Long id,
            @Param("processing") IntegrationTaskStatus processing,
            @Param("cutoff") Instant cutoff
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from IntegrationTask task where task.id = :id")
    Optional<IntegrationTask> findByIdForUpdate(@Param("id") Long id);
}
