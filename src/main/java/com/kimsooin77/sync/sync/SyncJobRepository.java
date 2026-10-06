package com.kimsooin77.sync.sync;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SyncJobRepository extends JpaRepository<SyncJob, Long> {

    @Query("select job from SyncJob job where (:status is null or job.status = :status)")
    Page<SyncJob> search(@Param("status") SyncJobStatus status, Pageable pageable);
}
