package com.kimsooin77.sync.api.sync;

import com.kimsooin77.sync.sync.EmployeeSyncJobService;
import com.kimsooin77.sync.sync.SyncJobResult;
import com.kimsooin77.sync.sync.SyncJobQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Optional;
import com.kimsooin77.sync.api.common.PageRequestFactory;
import com.kimsooin77.sync.api.common.PageResponse;
import com.kimsooin77.sync.sync.SyncJobStatus;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/sync-jobs")
public class SyncJobController {

    private final EmployeeSyncJobService employeeSyncJobService;
    private final SyncJobQueryService syncJobQueryService;

    public SyncJobController(
            EmployeeSyncJobService employeeSyncJobService,
            SyncJobQueryService syncJobQueryService
    ) {
        this.employeeSyncJobService = employeeSyncJobService;
        this.syncJobQueryService = syncJobQueryService;
    }

    @PostMapping
    public ResponseEntity<SyncJobResponse> createSyncJob() {
        SyncJobResult result = employeeSyncJobService.synchronizeFromHr();
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}")
                        .buildAndExpand(result.id())
                        .toUri())
                .body(SyncJobResponse.from(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SyncJobResponse> getSyncJob(@PathVariable Long id) {
        SyncJobResult result = syncJobQueryService.findById(id).orElseThrow(() -> new com.kimsooin77.sync.sync.SyncJobNotFoundException(id));
        return ResponseEntity.ok(SyncJobResponse.from(result));
    }

    @GetMapping
    public PageResponse<SyncJobListResponse> list(@RequestParam(required = false) SyncJobStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequestFactory.create(page, size, "startedAt", "id")
                .withSort(Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id")));
        return PageResponse.from(syncJobQueryService.search(status, pageable).map(SyncJobListResponse::from));
    }
}
