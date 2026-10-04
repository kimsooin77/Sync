package com.kimsooin77.sync.sync;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Service
public class SyncJobQueryService {

    private final SyncJobRepository syncJobRepository;

    public SyncJobQueryService(SyncJobRepository syncJobRepository) {
        this.syncJobRepository = Objects.requireNonNull(syncJobRepository, "syncJobRepository");
    }

    @Transactional(readOnly = true)
    public Optional<SyncJobResult> findById(Long syncJobId) {
        Objects.requireNonNull(syncJobId, "syncJobId");
        return syncJobRepository.findById(syncJobId).map(SyncJobResult::from);
    }
}
