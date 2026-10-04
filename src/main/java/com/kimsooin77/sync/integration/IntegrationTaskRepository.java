package com.kimsooin77.sync.integration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IntegrationTaskRepository extends JpaRepository<IntegrationTask, Long> {

    List<IntegrationTask> findAllByEmployee_IdOrderByIdAsc(Long employeeId);

    List<IntegrationTask> findAllBySyncItem_Id(Long syncItemId);
}
