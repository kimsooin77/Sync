package com.kimsooin77.sync.api.integration;

import com.kimsooin77.sync.integration.IntegrationTaskNotFoundException;
import com.kimsooin77.sync.integration.IntegrationTaskRetryRejectedException;
import com.kimsooin77.sync.integration.IntegrationTaskRetryService;
import com.kimsooin77.sync.api.common.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/integration-tasks")
public class IntegrationTaskRetryController {
    private final IntegrationTaskRetryService retryService;

    public IntegrationTaskRetryController(IntegrationTaskRetryService retryService) {
        this.retryService = retryService;
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<IntegrationTaskRetryResponse> retry(@PathVariable Long id) {
        return ResponseEntity.ok(IntegrationTaskRetryResponse.from(retryService.retry(id)));
    }

    @ExceptionHandler(IntegrationTaskNotFoundException.class)
    public ResponseEntity<ApiError> notFound(IntegrationTaskNotFoundException ignored) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("TASK_NOT_FOUND", "연계 작업을 찾을 수 없습니다."));
    }

    @ExceptionHandler(IntegrationTaskRetryRejectedException.class)
    public ResponseEntity<ApiError> rejected(IntegrationTaskRetryRejectedException failure) {
        String message = failure.getCode().equals("TASK_NOT_FAILED")
                ? "작업이 실패 상태가 아닙니다." : "이 작업은 수동 재처리 대상이 아닙니다.";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(failure.getCode(), message));
    }
}
