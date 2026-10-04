package com.kimsooin77.sync.sync;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class SyncJobTransactionService {

    private final SyncJobRepository syncJobRepository;
    private final SyncItemRepository syncItemRepository;

    public SyncJobTransactionService(
            SyncJobRepository syncJobRepository,
            SyncItemRepository syncItemRepository
    ) {
        this.syncJobRepository = Objects.requireNonNull(syncJobRepository, "syncJobRepository");
        this.syncItemRepository = Objects.requireNonNull(syncItemRepository, "syncItemRepository");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long start(int totalCount) {
        return syncJobRepository.saveAndFlush(new SyncJob(totalCount)).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncJobResult complete(Long syncJobId) {
        SyncJob job = findJob(syncJobId);
        Counts counts = counts(syncJobId);
        job.complete(counts.inserted(), counts.updated(), counts.skipped(), counts.failed());
        syncJobRepository.flush();
        return SyncJobResult.from(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long syncJobId) {
        SyncJob job = findJob(syncJobId);
        Counts counts = counts(syncJobId);
        job.fail(counts.inserted(), counts.updated(), counts.skipped(), counts.failed());
        syncJobRepository.flush();
    }

    private SyncJob findJob(Long syncJobId) {
        return syncJobRepository.findById(syncJobId)
                .orElseThrow(() -> new IllegalStateException("sync job was not found: " + syncJobId));
    }

    private Counts counts(Long syncJobId) {
        return new Counts(
                Math.toIntExact(syncItemRepository.countBySyncJob_IdAndResult(syncJobId, SyncItemResult.INSERTED)),
                Math.toIntExact(syncItemRepository.countBySyncJob_IdAndResult(syncJobId, SyncItemResult.UPDATED)),
                Math.toIntExact(syncItemRepository.countBySyncJob_IdAndResult(syncJobId, SyncItemResult.SKIPPED)),
                Math.toIntExact(syncItemRepository.countBySyncJob_IdAndResult(syncJobId, SyncItemResult.FAILED)));
    }

    private record Counts(int inserted, int updated, int skipped, int failed) {
    }
}
