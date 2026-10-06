package com.kimsooin77.sync.integration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IntegrationAttemptRepository extends JpaRepository<IntegrationAttempt, Long> {

    List<IntegrationAttempt> findAllByIntegrationTask_IdOrderByAttemptNoAsc(Long integrationTaskId);

    Page<IntegrationAttempt> findAllByIntegrationTask_IdOrderByAttemptNoAsc(Long integrationTaskId, Pageable pageable);

    @Query(
            "select max(attempt.attemptNo) from IntegrationAttempt attempt "
                    + "where attempt.integrationTask.id = :integrationTaskId")
    Integer findMaximumAttemptNo(@Param("integrationTaskId") Long integrationTaskId);
}
