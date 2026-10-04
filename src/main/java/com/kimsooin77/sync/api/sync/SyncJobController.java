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
        Optional<SyncJobResult> result = syncJobQueryService.findById(id);
        return result.map(SyncJobResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
