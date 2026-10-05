package com.kimsooin77.sync.api.integration;

import com.kimsooin77.sync.integration.IntegrationTaskNotFoundException;
import com.kimsooin77.sync.integration.IntegrationTaskRetryRejectedException;
import com.kimsooin77.sync.integration.IntegrationTaskRetryService;
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
    public ResponseEntity<Void> notFound(IntegrationTaskNotFoundException ignored) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(IntegrationTaskRetryRejectedException.class)
    public ResponseEntity<RetryErrorResponse> rejected(IntegrationTaskRetryRejectedException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new RetryErrorResponse(failure.getCode(),
                failure.getCode().equals("TASK_NOT_FAILED")
                        ? "Only failed integration tasks can be retried"
                        : "This integration task is not eligible for manual retry"));
    }

    public record RetryErrorResponse(String code, String message) {
    }
}
