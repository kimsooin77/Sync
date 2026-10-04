package com.kimsooin77.sync.sync;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SyncItemRepository extends JpaRepository<SyncItem, Long> {

    List<SyncItem> findAllBySyncJob_IdOrderByRowNumberAsc(Long syncJobId);

    long countBySyncJob_IdAndResult(Long syncJobId, SyncItemResult result);
}
