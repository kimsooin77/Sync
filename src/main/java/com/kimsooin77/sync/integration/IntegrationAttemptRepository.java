package com.kimsooin77.sync.integration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IntegrationAttemptRepository extends JpaRepository<IntegrationAttempt, Long> {

    List<IntegrationAttempt> findAllByIntegrationTask_IdOrderByAttemptNoAsc(Long integrationTaskId);
}
